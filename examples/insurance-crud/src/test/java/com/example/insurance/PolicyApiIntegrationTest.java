package com.example.insurance;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.insurance.policy.PolicyRepository;
import com.fasterxml.jackson.databind.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.*;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.*;
import org.springframework.web.bind.annotation.*;

@org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability
@SpringBootTest(properties = {"spring.h2.console.enabled=false", "insurance.demo.port=0"})
@AutoConfigureMockMvc
@Import(PolicyApiIntegrationTest.ProbeConfiguration.class)
class PolicyApiIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper mapper;
  @Autowired PolicyRepository repository;
  private ListAppender<ILoggingEvent> appender;
  private Logger logger;

  @BeforeEach
  void setup() {
    repository.deleteAll();
    logger = (Logger) LoggerFactory.getLogger("common.logging");
    appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
  }

  @AfterEach
  void cleanup() {
    logger.detachAppender(appender);
    appender.stop();
    assertThat(org.slf4j.MDC.get("traceId")).isNull();
  }

  @Test
  void fullCrudPersistsInH2AndDeletesPolicy() throws Exception {
    var created = create("POL-2026-123456");
    long id = mapper.readTree(created.getResponse().getContentAsString()).path("id").asLong();
    assertThat(created.getResponse().getHeader("Location")).isEqualTo("/api/policies/" + id);
    assertThat(repository.findById(id)).isPresent();
    mvc.perform(get("/api/policies/{id}", id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("holderName").value("Budi Santoso"))
        .andExpect(jsonPath("holderEmail").value("budi@example.test"));
    mvc.perform(get("/api/policies"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("items.length()").value(1))
        .andExpect(jsonPath("items[0].id").value(id));
    mvc.perform(
            put("/api/policies/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    request(
                        "POL-UPDATED-654321",
                        "Siti Aminah",
                        "siti@example.test",
                        "98765.43",
                        "INACTIVE")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("policyNumber").value("POL-UPDATED-654321"))
        .andExpect(jsonPath("status").value("INACTIVE"));
    assertThat(repository.findById(id).orElseThrow().getPremium())
        .isEqualByComparingTo(new BigDecimal("98765.43"));
    mvc.perform(delete("/api/policies/{id}", id))
        .andExpect(status().isNoContent())
        .andExpect(content().string(""));
    assertThat(repository.findById(id)).isEmpty();
    mvc.perform(get("/api/policies/{id}", id))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("code").value("POLICY_NOT_FOUND"));
  }

  @Test
  void payloadsAreMaskedButResponseIsUnchangedAndLocationIsAutomatic() throws Exception {
    var result = create("POL-2026-123456");
    JsonNode response = mapper.readTree(result.getResponse().getContentAsString());
    assertThat(response.path("holderName").asText()).isEqualTo("Budi Santoso");
    assertThat(response.path("policyNumber").asText()).isEqualTo("POL-2026-123456");
    List<JsonNode> events = events();
    assertThat(events)
        .allSatisfy(
            event ->
                assertThat(event.path("traceId").asText())
                    .isEqualTo("7f5f5fa5fb7917e4041826a490831dd0"));
    assertThat(count(events, "REQUEST_PAYLOAD")).isEqualTo(1);
    assertThat(count(events, "RESPONSE_PAYLOAD")).isEqualTo(1);
    JsonNode start = event(events, "PROCESS_START");
    assertThat(start.path("className").asText())
        .isEqualTo("com.example.insurance.policy.PolicyService");
    assertThat(start.path("methodName").asText()).isEqualTo("create");
    assertThat(start.path("payload").path("policyNumber").asText()).isEqualTo("***3456");
    assertThat(event(events, "PROCESS_END").path("payload").path("id").asLong())
        .isEqualTo(response.path("id").asLong());
    assertThat(event(events, "PROCESS_END").path("payload").path("holderName").asText())
        .isEqualTo("***");
    assertThat(event(events, "PROCESS_END").path("outcome").asText()).isEqualTo("SUCCESS");
    assertThat(appender.list.toString())
        .doesNotContain("Budi Santoso", "budi@example.test", "POL-2026-123456");
    assertThat(event(events, "REQUEST_PAYLOAD").path("payload").path("policyNumber").asText())
        .isEqualTo("***3456");
  }

  @Test
  void invalidRequestReturnsSafeFieldCodesWithoutSavingOrStackTrace() throws Exception {
    mvc.perform(
            post("/api/policies")
                .header("traceparent", TraceFixtures.header("5a9e2a67ed93fe302ed3e266996e235b"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(request("POL-INVALID", "", "not-an-email", "-10.00", "ACTIVE")))
        .andExpect(status().isBadRequest())
        .andExpect(header().string("X-Trace-ID", "5a9e2a67ed93fe302ed3e266996e235b"))
        .andExpect(jsonPath("code").value("INVALID_INPUT"))
        .andExpect(jsonPath("traceId").value("5a9e2a67ed93fe302ed3e266996e235b"))
        .andExpect(jsonPath("fieldErrors").isNotEmpty());
    assertThat(repository.count()).isZero();
    assertThat(events().stream().anyMatch(event -> event.has("stackTrace"))).isFalse();
    assertThat(appender.list.toString()).doesNotContain("not-an-email");
  }

  @Test
  void duplicatePolicyNumberReturns409AndPreservesOriginal() throws Exception {
    create("POL-DUPLICATE");
    appender.list.clear();
    mvc.perform(
            post("/api/policies")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    request(
                        "POL-DUPLICATE",
                        "Different Name",
                        "other@example.test",
                        "100.00",
                        "ACTIVE")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("code").value("POLICY_NUMBER_ALREADY_EXISTS"));
    assertThat(repository.count()).isEqualTo(1);
    assertThat(events().stream().anyMatch(event -> event.has("stackTrace"))).isFalse();
    assertThat(appender.list.toString())
        .doesNotContain("Different Name", "other@example.test", "POL-DUPLICATE");
  }

  @Test
  void missingPolicyIs404AndListInitiallyEmpty() throws Exception {
    mvc.perform(get("/api/policies"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("items").isEmpty());
    mvc.perform(get("/api/policies/999999"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("code").value("POLICY_NOT_FOUND"));
    mvc.perform(delete("/api/policies/999999")).andExpect(status().isNotFound());
  }

  @Test
  void updateCannotTakeAnotherPolicyNumberAndLeavesDatabaseUnchanged() throws Exception {
    var first = create("POL-FIRST");
    create("POL-SECOND");
    long id = mapper.readTree(first.getResponse().getContentAsString()).path("id").asLong();
    mvc.perform(
            put("/api/policies/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    request(
                        "POL-SECOND", "Changed Name", "changed@example.test", "1.00", "INACTIVE")))
        .andExpect(status().isConflict());
    assertThat(repository.findById(id).orElseThrow().getPolicyNumber()).isEqualTo("POL-FIRST");
  }

  @Test
  void malformedJsonInvalidEnumAndPathKeep400RatherThan500() throws Exception {
    mvc.perform(
            post("/api/policies")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{private-malformed"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("code").value("INVALID_REQUEST"));
    assertThat(count(events(), "REQUEST_PAYLOAD")).isZero();
    mvc.perform(
            post("/api/policies")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request("POL-ENUM", "Budi", "budi@example.test", "10.00", "UNKNOWN")))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/api/policies/not-a-number")).andExpect(status().isBadRequest());
    mvc.perform(get("/api/policies/-1")).andExpect(status().isBadRequest());
    mvc.perform(patch("/api/policies/1")).andExpect(status().isMethodNotAllowed());
    assertThat(appender.list.toString()).doesNotContain("private-malformed");
    assertThat(events().stream().anyMatch(event -> event.has("stackTrace"))).isFalse();
  }

  @Test
  void unexpectedExceptionHasOneStackTraceAndSafe500Response() throws Exception {
    mvc.perform(
            get("/__test/bug")
                .header("traceparent", TraceFixtures.header("b2ce8cfe496cf568df794a12e18b15b5")))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("code").value("INTERNAL_ERROR"))
        .andExpect(jsonPath("message").value("An unexpected error occurred"))
        .andExpect(jsonPath("traceId").value("b2ce8cfe496cf568df794a12e18b15b5"));
    assertThat(events().stream().filter(event -> event.has("stackTrace"))).hasSize(1);
    assertThat(event(events(), "UNHANDLED_ERROR").path("errorOrigin").path("methodName").asText())
        .isEqualTo("bug");
    assertThat(appender.list.toString()).doesNotContain("private-medical-message");
  }

  MvcResult create(String policyNumber) throws Exception {
    return mvc.perform(
            post("/api/policies")
                .header("traceparent", TraceFixtures.header("7f5f5fa5fb7917e4041826a490831dd0"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    request(
                        policyNumber, "Budi Santoso", "budi@example.test", "1250000.50", "ACTIVE")))
        .andExpect(status().isCreated())
        .andReturn();
  }

  String request(String policy, String name, String email, String premium, String status)
      throws Exception {
    return mapper.writeValueAsString(
        Map.of(
            "policyNumber",
            policy,
            "holderName",
            name,
            "holderEmail",
            email,
            "premium",
            new BigDecimal(premium),
            "status",
            status));
  }

  List<JsonNode> events() {
    return appender.list.stream()
        .map(
            event -> {
              try {
                java.util.Map<String, Object> fields = new java.util.LinkedHashMap<>();
                event.getKeyValuePairs().forEach(kv -> fields.put(kv.key, kv.value));
                return mapper.<JsonNode>valueToTree(fields);
              } catch (Exception error) {
                throw new AssertionError(error);
              }
            })
        .toList();
  }

  long count(List<JsonNode> events, String type) {
    return events.stream().filter(event -> type.equals(event.path("event").asText())).count();
  }

  JsonNode event(List<JsonNode> events, String type) {
    return events.stream()
        .filter(event -> type.equals(event.path("event").asText()))
        .findFirst()
        .orElseThrow();
  }

  @TestConfiguration(proxyBeanMethods = false)
  @Import(BugProbe.class)
  static class ProbeConfiguration {}

  /** Test-only route; deliberately not part of the packaged CRUD application. */
  @RestController
  static class BugProbe {
    @GetMapping("/__test/bug")
    String bug() {
      throw new NullPointerException("private-medical-message");
    }
  }
}
