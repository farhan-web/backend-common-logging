package com.example.insurance;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.insurance.claim.*;
import com.example.insurance.demo.LocalIntegrationServer;
import com.example.insurance.demo.LocalIntegrationServer.Fault;
import com.example.insurance.policy.*;
import com.fasterxml.jackson.databind.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.*;

@org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability
@SpringBootTest(
    properties = {
      "insurance.demo.port=0",
      "spring.h2.console.enabled=false",
      "spring.datasource.url=jdbc:h2:mem:claim-flow-tests;DB_CLOSE_DELAY=-1"
    })
@AutoConfigureMockMvc
@org.springframework.context.annotation.Import(ClaimFlowIntegrationTest.TracingCapture.class)
class ClaimFlowIntegrationTest {
  @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods = false)
  static class TracingCapture {
    @org.springframework.context.annotation.Bean
    io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter spanExporter() {
      return io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter.create();
    }

    @org.springframework.context.annotation.Bean
    io.opentelemetry.sdk.trace.SpanProcessor captureSpans(
        io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter exporter) {
      return io.opentelemetry.sdk.trace.export.SimpleSpanProcessor.create(exporter);
    }
  }

  @Autowired io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter exporter;

  @Autowired io.github.commonlogging.internal.LoggingHealth loggingHealth;
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper mapper;
  @Autowired LocalIntegrationServer simulator;
  @Autowired ClaimRepository claims;
  @Autowired PolicyRepository policies;
  private Logger logger;
  private ListAppender<ILoggingEvent> appender;
  private static final String POLICY = "POL-COMPLEX-123456";
  private static final String SECRET_DOCUMENT = "private medical document contents";

  @BeforeEach
  void setup() throws Exception {
    simulator.awaitIdle();
    simulator.reset();
    exporter.reset();
    claims.deleteAll();
    policies.deleteAll();
    policies.saveAndFlush(
        new Policy(
            new PolicyRequest(
                POLICY,
                "Private Holder",
                "holder@example.test",
                new BigDecimal("100.00"),
                PolicyStatus.ACTIVE)));
    logger = (Logger) LoggerFactory.getLogger("common.logging");
    appender = new ListAppender<>();
    appender.list = new CopyOnWriteArrayList<>();
    appender.start();
    logger.addAppender(appender);
  }

  @AfterEach
  void cleanup() throws Exception {
    simulator.awaitIdle();
    logger.detachAppender(appender);
    appender.stop();
    assertThat(org.slf4j.MDC.get("traceId")).isNull();
  }

  @Test
  void completeFlowUsesActualHttpAndSdkWithOneTraceAndSafePayloads() throws Exception {
    var result = submit("success-key", "1d8f545ec1831c886f060d746b82d980", request("1000.00"));
    assertThat(result.getResponse().getStatus()).isEqualTo(201);
    JsonNode response = mapper.readTree(result.getResponse().getContentAsString());
    assertThat(response.path("status").asText()).isEqualTo("SUBMITTED");
    assertThat(
            simulator.objectContent(
                "insurance-documents", "claims/" + response.path("id").asText() + "/document"))
        .isEqualTo(SECRET_DOCUMENT.getBytes(StandardCharsets.UTF_8));
    assertThat(response.path("notificationStatus").asText()).isEqualTo("SENT");
    assertThat(simulator.state())
        .containsEntry("objects", 1)
        .containsEntry("coreClaims", 1)
        .containsEntry("notifications", 1);
    @SuppressWarnings("unchecked")
    var traces = (Map<String, String>) simulator.state().get("traces");
    assertThat(traces)
        .containsEntry("core.verify", "1d8f545ec1831c886f060d746b82d980")
        .containsEntry("core.submit", "1d8f545ec1831c886f060d746b82d980")
        .containsEntry("s3.put", "1d8f545ec1831c886f060d746b82d980")
        .containsEntry("notification.send", "1d8f545ec1831c886f060d746b82d980");
    List<JsonNode> events = events();
    List<String> starts =
        events.stream()
            .filter(e -> "DEPENDENCY_START".equals(e.path("event").asText()))
            .map(e -> e.path("operation").asText())
            .toList();
    assertThat(starts)
        .containsExactly(
            "core.policy.verify",
            "s3.document.put",
            "core.claim.submit",
            "notification.claim.send");
    JsonNode operation =
        events.stream()
            .filter(
                e ->
                    "claim.submit".equals(e.path("operation").asText())
                        && "PROCESS_START".equals(e.path("event").asText()))
            .findFirst()
            .orElseThrow();
    assertThat(events.stream().filter(e -> "DEPENDENCY_START".equals(e.path("event").asText())))
        .allSatisfy(
            e ->
                assertThat(e.path("parentSpanId").asText())
                    .isEqualTo(operation.path("spanId").asText()));
    JsonNode root =
        events.stream()
            .filter(e -> "REQUEST_START".equals(e.path("event").asText()))
            .findFirst()
            .orElseThrow();
    assertThat(operation.path("parentSpanId")).isEqualTo(root.path("spanId"));
    assertThat(operation.path("payload").path("policyNumber").asText()).isEqualTo("***3456");
    assertThat(operation.path("payload").has("documentBase64")).isFalse();
    JsonNode claimEnd =
        events.stream()
            .filter(
                e ->
                    "PROCESS_END".equals(e.path("event").asText())
                        && "claim.submit".equals(e.path("operation").asText()))
            .findFirst()
            .orElseThrow();
    assertThat(claimEnd.path("payload").path("claim").path("status").asText())
        .isEqualTo("SUBMITTED");
    assertThat(claimEnd.path("payload").path("claim").path("policyNumber").asText())
        .isEqualTo("***3456");
    Map<String, String> remoteNames =
        Map.of(
            "core.policy.verify",
            "simulator.core.verify",
            "s3.document.put",
            "simulator.s3.put",
            "core.claim.submit",
            "simulator.core.submit",
            "notification.claim.send",
            "simulator.notification.send");
    for (var mapping : remoteNames.entrySet()) {
      JsonNode client =
          events.stream()
              .filter(
                  e ->
                      "DEPENDENCY_START".equals(e.path("event").asText())
                          && mapping.getKey().equals(e.path("operation").asText()))
              .findFirst()
              .orElseThrow();
      JsonNode server =
          events.stream()
              .filter(
                  e ->
                      "PROCESS_START".equals(e.path("event").asText())
                          && mapping.getValue().equals(e.path("operation").asText()))
              .findFirst()
              .orElseThrow();
      if (mapping.getKey().equals("s3.document.put")) {
        assertThat(server.path("parentSpanId")).isEqualTo(client.path("spanId"));
      } else {
        // Boot instruments the actual HTTP exchange below the logical integration scope.
        var wire =
            exporter.getFinishedSpanItems().stream()
                .filter(span -> span.getSpanId().equals(server.path("parentSpanId").asText()))
                .findFirst()
                .orElseThrow();
        assertThat(wire.getKind()).isEqualTo(io.opentelemetry.api.trace.SpanKind.CLIENT);
        assertThat(wire.getParentSpanId()).isEqualTo(client.path("spanId").asText());
        assertThat(wire.getTraceId()).isEqualTo(server.path("traceId").asText());
      }
      assertThat(server.path("spanId")).isNotEqualTo(client.path("spanId"));
      assertThat(server.path("spanKind").asText()).isEqualTo("SERVER");
    }
    assertThat(events)
        .allSatisfy(
            e ->
                assertThat(e.path("traceId").asText())
                    .isEqualTo("1d8f545ec1831c886f060d746b82d980"));
    var console =
        (ch.qos.logback.core.OutputStreamAppender<ILoggingEvent>)
            logger
                .getLoggerContext()
                .getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)
                .getAppender("CONSOLE");
    var startEvent =
        appender.list.stream()
            .filter(
                e ->
                    e.getKeyValuePairs().stream()
                        .anyMatch(kv -> kv.key.equals("event") && kv.value.equals("PROCESS_START")))
            .findFirst()
            .orElseThrow();
    JsonNode encoded =
        new ObjectMapper(
                com.fasterxml.jackson.core.JsonFactory.builder()
                    .enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                    .build())
            .readTree(console.getEncoder().encode(startEvent));
    assertThat(encoded.path("payload").isObject()).isTrue();
    assertThat(encoded.path("traceId").asText()).isEqualTo("1d8f545ec1831c886f060d746b82d980");
    assertThat(encoded.path("message").asText()).isEqualTo("common.logging.event");
    assertThat(encoded.toString())
        .doesNotContain(SECRET_DOCUMENT, "private diagnosis", "claimant@example.test");
    for (ILoggingEvent logEvent : appender.list) {
      JsonNode line =
          new ObjectMapper(
                  com.fasterxml.jackson.core.JsonFactory.builder()
                      .enable(
                          com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                      .build())
              .readTree(console.getEncoder().encode(logEvent));
      assertThat(line.path("event").isTextual()).isTrue();
    }
    assertThat(loggingHealth.backendErrors()).as("No silently rejected structured events").isZero();
    var exported = exporter.getFinishedSpanItems();
    assertThat(exported).isNotEmpty();
    for (JsonNode event : events) {
      assertThat(
              exported.stream()
                  .anyMatch(span -> span.getSpanId().equals(event.path("spanId").asText())))
          .as("Log span %s must be a real exported SDK span", event.path("spanId").asText())
          .isTrue();
    }
    assertThat(logs())
        .doesNotContain(
            SECRET_DOCUMENT,
            Base64.getEncoder().encodeToString(SECRET_DOCUMENT.getBytes(StandardCharsets.UTF_8)),
            "private diagnosis",
            "claimant@example.test",
            POLICY);
    assertThat(events.stream().filter(e -> e.has("stackTrace"))).isEmpty();
  }

  @Test
  void coreRejectionCompensatesS3AndRecordsRejectedWithoutStackTrace() throws Exception {
    simulator.fault("core.submit", Fault.REJECT);
    var result = submit("reject-key", "be79fe9e71dd296df28b84e4b3827d39", request("1000.00"));
    assertThat(result.getResponse().getStatus()).isEqualTo(422);
    assertThat(claims.findAll().get(0).getStatus()).isEqualTo(ClaimStatus.REJECTED);
    assertThat(simulator.state()).containsEntry("objects", 0).containsEntry("coreClaims", 0);
    assertThat(
            events().stream()
                .anyMatch(
                    e ->
                        "s3.document.delete".equals(e.path("operation").asText())
                            && "DEPENDENCY_END".equals(e.path("event").asText())))
        .isTrue();
    assertThat(events().stream().filter(e -> e.has("stackTrace"))).isEmpty();
  }

  @Test
  void uploadMayHaveWrittenObjectAndFailedCleanupPreservesPrimaryError() throws Exception {
    simulator.fault("s3.put", Fault.ERROR_AFTER_WRITE);
    simulator.fault("s3.delete", Fault.UNAVAILABLE);
    var result = submit("compensation-key", "5994aa38d0f4fc2b42a256fde0184fd5", request("1000.00"));
    assertThat(result.getResponse().getStatus()).isEqualTo(503);
    assertThat(mapper.readTree(result.getResponse().getContentAsString()).path("code").asText())
        .isEqualTo("S3_PUT_FAILURE");
    assertThat(claims.findAll().get(0).getStatus()).isEqualTo(ClaimStatus.COMPENSATION_REQUIRED);
    assertThat(simulator.state()).containsEntry("objects", 1).containsEntry("coreClaims", 0);
    assertThat(events().stream().filter(e -> e.has("stackTrace"))).hasSize(2);
    assertThat(
            events().stream()
                .filter(e -> "DEPENDENCY_FAILED".equals(e.path("event").asText()))
                .map(e -> e.path("errorCode").asText()))
        .contains("S3_PUT_FAILURE", "S3_DELETE_FAILURE");
  }

  @Test
  void timeoutAfterCoreAcceptKeepsDocumentAndReconcilesWithoutDuplicateSubmission()
      throws Exception {
    simulator.fault("core.submit", Fault.TIMEOUT_AFTER_ACCEPT);
    var first = submit("timeout-key", "4798cde0847e7b499296f32d1cd4765e", request("1000.00"));
    assertThat(first.getResponse().getStatus()).isEqualTo(202);
    JsonNode pending = mapper.readTree(first.getResponse().getContentAsString());
    String id = pending.path("id").asText();
    assertThat(pending.path("status").asText()).isEqualTo("PENDING_RECONCILIATION");
    assertThat(simulator.state())
        .containsEntry("coreClaims", 1)
        .containsEntry("objects", 1)
        .containsEntry("notifications", 0);
    var replay = submit("timeout-key", "9637fcb0a59e71952c9b71c1b7bb7d50", request("1000.00"));
    assertThat(replay.getResponse().getStatus()).isEqualTo(202);
    simulator.fault("core.lookup", Fault.REJECT);
    mvc.perform(
            post("/api/claims/{id}/reconcile", id)
                .header("traceparent", TraceFixtures.header("ecb7686303357dc45768ac0c394c29a2")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("status").value("PENDING_RECONCILIATION"));
    mvc.perform(
            post("/api/claims/{id}/reconcile", id)
                .header("traceparent", TraceFixtures.header("95adfb5350c2a379c356a9f41bec7d3b")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("status").value("SUBMITTED"))
        .andExpect(jsonPath("notificationStatus").value("SENT"));
    assertThat(simulator.state())
        .containsEntry("coreClaims", 1)
        .containsEntry("objects", 1)
        .containsEntry("notifications", 1);
    assertThat(
            events().stream()
                .filter(
                    e ->
                        "core.claim.submit".equals(e.path("operation").asText())
                            && "DEPENDENCY_START".equals(e.path("event").asText())))
        .hasSize(1);
    assertThat(events().stream().filter(e -> e.has("stackTrace"))).hasSize(1);
    assertThat(
            events().stream()
                .filter(e -> "DEPENDENCY_FAILED".equals(e.path("event").asText()))
                .map(e -> e.path("errorCategory").asText()))
        .contains("DEPENDENCY_TIMEOUT");
    assertThat(
            events().stream()
                .anyMatch(e -> "s3.document.delete".equals(e.path("operation").asText())))
        .isFalse();
  }

  @Test
  void notificationFailureDoesNotUndoAcceptedClaimAndCanBeRetriedIdempotently() throws Exception {
    simulator.fault("notification.send", Fault.UNAVAILABLE);
    var first = submit("notification-key", "e8f53ef8e43c625797468717c2451aae", request("1000.00"));
    assertThat(first.getResponse().getStatus()).isEqualTo(201);
    JsonNode response = mapper.readTree(first.getResponse().getContentAsString());
    assertThat(response.path("status").asText()).isEqualTo("SUBMITTED");
    assertThat(response.path("notificationStatus").asText()).isEqualTo("PENDING");
    mvc.perform(post("/api/claims/{id}/reconcile", response.path("id").asText()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("notificationStatus").value("SENT"));
    assertThat(simulator.state())
        .containsEntry("coreClaims", 1)
        .containsEntry("objects", 1)
        .containsEntry("notifications", 1);
    assertThat(events().stream().filter(e -> e.has("stackTrace"))).hasSize(1);
  }

  @Test
  void coreVerificationRejectionIsBusinessRejectionBeforeUpload() throws Exception {
    simulator.fault("core.verify", Fault.REJECT);
    assertThat(
            submit("verify-reject-key", "4a004d02d3ca490190dd34aafaed2e0b", request("1000.00"))
                .getResponse()
                .getStatus())
        .isEqualTo(422);
    assertThat(claims.findAll().get(0).getStatus()).isEqualTo(ClaimStatus.REJECTED);
    assertThat(simulator.state()).containsEntry("objects", 0).containsEntry("coreClaims", 0);
    assertThat(events().stream().anyMatch(e -> e.has("stackTrace"))).isFalse();
  }

  @Test
  void invalidCoreResponseFailsBeforeUploadAndIs502() throws Exception {
    simulator.fault("core.verify", Fault.INVALID_RESPONSE);
    assertThat(
            submit("invalid-core", "a94b3781e62e357986d1eabdf77b7c5d", request("1000.00"))
                .getResponse()
                .getStatus())
        .isEqualTo(502);
    assertThat(claims.findAll().get(0).getStatus()).isEqualTo(ClaimStatus.FAILED);
    assertThat(simulator.state()).containsEntry("objects", 0).containsEntry("coreClaims", 0);
  }

  @Test
  void inactiveLocalPolicyStopsBeforeAnyExternalCall() throws Exception {
    Policy policy = policies.findByPolicyNumber(POLICY).orElseThrow();
    policy.update(
        new PolicyRequest(
            POLICY,
            "Private Holder",
            "holder@example.test",
            new BigDecimal("100.00"),
            PolicyStatus.INACTIVE));
    policies.saveAndFlush(policy);
    assertThat(
            submit("inactive-key", "1fe2cc7f82625f5a46994e64221fa2af", request("1000.00"))
                .getResponse()
                .getStatus())
        .isEqualTo(422);
    assertThat(claims.findAll().get(0).getStatus()).isEqualTo(ClaimStatus.REJECTED);
    assertThat(events().stream().filter(e -> "DEPENDENCY_START".equals(e.path("event").asText())))
        .isEmpty();
  }

  @Test
  void keyReplayDoesNotRepeatDependenciesAndChangedPayloadIs409() throws Exception {
    assertThat(
            submit("replay-key", "10929586591592da976897dc7b86f65b", request("1000.00"))
                .getResponse()
                .getStatus())
        .isEqualTo(201);
    assertThat(
            submit("replay-key", "ea8c7e1ec80cec2af43d315c67ef5e4c", request("1000.00"))
                .getResponse()
                .getStatus())
        .isEqualTo(200);
    assertThat(
            submit("replay-key", "48cd147bf72e8672e1459a01cb4d920c", request("2000.00"))
                .getResponse()
                .getStatus())
        .isEqualTo(409);
    assertThat(simulator.state())
        .containsEntry("coreClaims", 1)
        .containsEntry("objects", 1)
        .containsEntry("notifications", 1);
  }

  @Test
  void invalidDocumentIs400AndDoesNotCreateClaimOrCallDependencies() throws Exception {
    Map<String, Object> input = request("1000.00");
    input.put("documentBase64", "not-base64!");
    assertThat(
            submit("invalid-doc", "5a432d12e2b43a40acc74b2532b1b52f", input)
                .getResponse()
                .getStatus())
        .isEqualTo(400);
    assertThat(claims.count()).isZero();
    assertThat(events().stream().filter(e -> "DEPENDENCY_START".equals(e.path("event").asText())))
        .isEmpty();
    assertThat(logs()).doesNotContain("not-base64!");
  }

  @Test
  void uploadTimeoutEvenWithSuccessfulDeleteRequiresVerification() throws Exception {
    simulator.fault("s3.put", Fault.TIMEOUT_AFTER_ACCEPT);
    var result =
        submit("upload-timeout-key", "9fbd2f64a7498a8e423348002d142508", request("1000.00"));
    assertThat(result.getResponse().getStatus()).isEqualTo(504);
    assertThat(claims.findAll().get(0).getStatus()).isEqualTo(ClaimStatus.COMPENSATION_REQUIRED);
    assertThat(simulator.state()).containsEntry("coreClaims", 0);
    assertThat(
            events().stream()
                .filter(e -> "DEPENDENCY_FAILED".equals(e.path("event").asText()))
                .map(e -> e.path("errorCategory").asText()))
        .contains("DEPENDENCY_TIMEOUT");
  }

  @Test
  void concurrentSameKeyCreatesOneRemoteClaim() throws Exception {
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      Future<MvcResult> a =
          pool.submit(
              () ->
                  submit("concurrent-key", "74ac525f0d7c8f625e67022b8d95ef6d", request("1000.00")));
      Future<MvcResult> b =
          pool.submit(
              () ->
                  submit("concurrent-key", "8215ab49a7f7090dd01d51baba392024", request("1000.00")));
      assertThat(a.get().getResponse().getStatus()).isIn(200, 201, 202);
      assertThat(b.get().getResponse().getStatus()).isIn(200, 201, 202);
      assertThat(claims.count()).isEqualTo(1);
      assertThat(simulator.state()).containsEntry("coreClaims", 1).containsEntry("objects", 1);
    } finally {
      pool.shutdownNow();
    }
  }

  Map<String, Object> request(String amount) {
    Map<String, Object> request = new LinkedHashMap<>();
    request.put("policyNumber", POLICY);
    request.put("amount", new BigDecimal(amount));
    request.put("claimantEmail", "claimant@example.test");
    request.put("description", "private diagnosis");
    request.put("contentType", "text/plain");
    request.put(
        "documentBase64",
        Base64.getEncoder().encodeToString(SECRET_DOCUMENT.getBytes(StandardCharsets.UTF_8)));
    return request;
  }

  MvcResult submit(String key, String trace, Map<String, Object> request) throws Exception {
    return mvc.perform(
            post("/api/claims")
                .header("Idempotency-Key", key)
                .header("traceparent", TraceFixtures.header(trace))
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(request)))
        .andReturn();
  }

  List<JsonNode> events() {
    return appender.list.stream()
        .map(
            e -> {
              try {
                java.util.Map<String, Object> fields = new java.util.LinkedHashMap<>();
                e.getKeyValuePairs().forEach(kv -> fields.put(kv.key, kv.value));
                return mapper.<JsonNode>valueToTree(fields);
              } catch (Exception ex) {
                throw new AssertionError(ex);
              }
            })
        .toList();
  }

  String logs() {
    return events().toString();
  }
}
