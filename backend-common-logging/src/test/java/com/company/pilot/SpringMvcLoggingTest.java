package com.company.pilot;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.*;
import io.github.commonlogging.annotation.*;
import io.github.commonlogging.api.*;
import io.github.commonlogging.internal.*;
import io.github.commonlogging.payload.MaskStrategy;
import io.github.commonlogging.spring.*;
import java.net.SocketTimeoutException;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.*;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

@SpringBootTest(
    classes = SpringMvcLoggingTest.App.class,
    properties = {
      "backend.common.logging.application=insurance-pilot",
      "backend.common.logging.application-package=com.company.pilot",
      "backend.common.logging.payload-enabled=true",
      "backend.common.logging.payload-paths[0]=/claims",
      "backend.common.logging.propagation-hosts[0]=policy.invalid"
    })
@AutoConfigureMockMvc
class SpringMvcLoggingTest {
  @Autowired MockMvc mvc;
  @Autowired CaptureSink sink;
  @Autowired RestTemplate client;

  @BeforeEach
  void clear() {
    sink.events.clear();
  }

  @AfterEach
  void clean() {
    assertThat(LogContext.traceId()).isNull();
    assertThat(org.slf4j.MDC.get("traceId")).isNull();
  }

  @Test
  void successLogsMaskedDtoAndActualClassWithoutChangingHttpBody() throws Exception {
    mvc.perform(
            post("/claims")
                .header("traceparent", TraceFixtures.header("4c18d52386a765eb0823f784630b484e"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"name\":\"Alice\",\"policy\":\"POL123456\",\"token\":\"secret-token\",\"mode\":\"ok\"}"))
        .andExpect(status().isOk())
        .andExpect(header().string("X-Trace-ID", "4c18d52386a765eb0823f784630b484e"))
        .andExpect(jsonPath("name").value("Alice"))
        .andExpect(jsonPath("policy").value("POL123456"));
    assertThat(count("REQUEST_START")).isEqualTo(1);
    assertThat(count("REQUEST_END")).isEqualTo(1);
    assertThat(count("REQUEST_PAYLOAD")).isEqualTo(1);
    assertThat(count("RESPONSE_PAYLOAD")).isEqualTo(1);
    assertThat(sink.events)
        .allSatisfy(
            e ->
                assertThat(e.path("traceId").asText())
                    .isEqualTo("4c18d52386a765eb0823f784630b484e"));
    assertThat(sink.events.toString())
        .doesNotContain("Alice", "secret-token", "POL123456")
        .contains("***3456");
    assertThat(event("PROCESS_START").path("className").asText())
        .isEqualTo(ClaimService.class.getName());
    assertThat(event("PROCESS_START").path("methodName").asText()).isEqualTo("submit");
    assertThat(event("PROCESS_START").path("payload").path("name").asText()).isEqualTo("***");
    assertThat(event("PROCESS_END").path("payload").path("policy").asText()).isEqualTo("***3456");
  }

  @Test
  void beanValidationHasPayloadButNoStackTraceOrRawRejectedValue() throws Exception {
    mvc.perform(
            post("/claims")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"name\":\"\",\"policy\":\"POL123456\",\"token\":\"secret-token\",\"mode\":\"ok\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("code").value("INVALID_INPUT"));
    assertThat(count("REQUEST_PAYLOAD")).isEqualTo(1);
    assertThat(event("PROCESS_REJECTED").has("stackTrace")).isFalse();
    assertThat(sink.events.toString()).doesNotContain("secret-token", "POL123456");
  }

  @Test
  void malformedBodyNeverFallsBackToRawPayload() throws Exception {
    mvc.perform(
            post("/claims")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{private-secret-malformed"))
        .andExpect(status().isBadRequest());
    assertThat(count("REQUEST_PAYLOAD")).isZero();
    assertThat(sink.events.toString()).doesNotContain("private-secret-malformed");
  }

  @Test
  void npeHasOneStackTraceAndFailureOperationAfterAspectUnwinds() throws Exception {
    mvc.perform(
            post("/claims")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Alice\",\"mode\":\"npe\"}"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("code").value("INTERNAL_ERROR"));
    JsonNode error = event("UNHANDLED_ERROR");
    assertThat(error.path("operation").asText()).isEqualTo("claim.submit");
    assertThat(error.path("errorOrigin").path("methodName").asText()).isEqualTo("submit");
    assertThat(sink.events.stream().filter(e -> e.has("stackTrace"))).hasSize(1);
    assertThat(count("PROCESS_INCOMPLETE")).isZero();
    assertThat(event("REQUEST_END").path("httpStatus").asInt()).isEqualTo(500);
    assertThat(sink.events.toString()).doesNotContain("sensitive-exception-message", "Alice");
  }

  @Test
  void downstreamTimeoutKeepsCauseAndDoesNotRetry() throws Exception {
    MockRestServiceServer server = MockRestServiceServer.bindTo(client).build();
    server
        .expect(requestTo("https://policy.invalid/lookup"))
        .andExpect(
            org.springframework.test.web.client.match.MockRestRequestMatchers.header(
                "traceparent",
                org.hamcrest.Matchers.matchesPattern(
                    "00-"
                        + TraceFixtures.id("4b414e70e808b10800a116e00ef44195")
                        + "-[0-9a-f]{16}-01")))
        .andRespond(withException(new SocketTimeoutException("private-token")));
    mvc.perform(
            post("/claims")
                .header("traceparent", TraceFixtures.header("4b414e70e808b10800a116e00ef44195"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Alice\",\"mode\":\"dependency\"}"))
        .andExpect(status().isGatewayTimeout())
        .andExpect(jsonPath("code").value("POLICY_TIMEOUT"));
    server.verify();
    assertThat(event("UNHANDLED_ERROR").path("errorCategory").asText())
        .isEqualTo("DEPENDENCY_TIMEOUT");
    assertThat(event("UNHANDLED_ERROR").path("stackTrace").size()).isGreaterThanOrEqualTo(2);
    assertThat(sink.events.toString()).doesNotContain("private-token");
  }

  @Test
  void invalidIdIsReplacedAndUnselectedEndpointDoesNotLogPayload() throws Exception {
    org.springframework.test.web.servlet.MvcResult result =
        mvc.perform(
                post("/other")
                    .header("traceparent", "not valid id")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"Alice\"}"))
            .andExpect(status().isOk())
            .andReturn();
    assertThat(result.getResponse().getHeader("X-Trace-ID"))
        .matches("[0-9a-f]{32}")
        .isNotEqualTo("not valid id");
    assertThat(count("REQUEST_PAYLOAD")).isZero();
    assertThat(count("RESPONSE_PAYLOAD")).isZero();
  }

  long count(String type) {
    return sink.events.stream().filter(e -> type.equals(e.path("event").asText())).count();
  }

  JsonNode event(String type) {
    return sink.events.stream()
        .filter(e -> type.equals(e.path("event").asText()))
        .findFirst()
        .orElseThrow(java.util.NoSuchElementException::new);
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration
  @Import({ClaimController.class, ExistingHandler.class})
  static class App {
    @Bean
    CaptureSink captureSink() {
      return new CaptureSink();
    }

    @Bean
    RestTemplate client(org.springframework.boot.web.client.RestTemplateBuilder builder) {
      return builder.build();
    }

    @Bean
    ClaimService claimService(DependencyLogging dependency, RestTemplate client) {
      return new ClaimService(dependency, client);
    }

    @Bean
    ErrorClassifier classifier() {
      return e -> {
        if (e instanceof MethodArgumentNotValidException
            || e instanceof org.springframework.http.converter.HttpMessageNotReadableException)
          return new ErrorDetails("VALIDATION", "INVALID_INPUT", true);
        if (e.getCause() instanceof SocketTimeoutException)
          return new ErrorDetails("DEPENDENCY_TIMEOUT", "POLICY_TIMEOUT", false);
        return new ErrorDetails("INTERNAL", "INTERNAL_ERROR", false);
      };
    }
  }

  static class CaptureSink implements EventSink {
    final List<JsonNode> events = new CopyOnWriteArrayList<>();
    final ObjectMapper mapper = new ObjectMapper();

    public void write(String level, String json) {
      try {
        events.add(mapper.readTree(json));
      } catch (Exception e) {
        throw new IllegalStateException(e);
      }
    }
  }

  @LogPayload
  static final class ClaimRequest {
    private final @NotBlank @LogMask String name;
    private final @LogMask(strategy = MaskStrategy.LAST_FOUR) String policy;
    private final @LogExclude String token;
    private final @LogValue String mode;

    @com.fasterxml.jackson.annotation.JsonCreator
    public ClaimRequest(
        @com.fasterxml.jackson.annotation.JsonProperty("name") String name,
        @com.fasterxml.jackson.annotation.JsonProperty("policy") String policy,
        @com.fasterxml.jackson.annotation.JsonProperty("token") String token,
        @com.fasterxml.jackson.annotation.JsonProperty("mode") String mode) {

      this.name = name;
      this.policy = policy;
      this.token = token;
      this.mode = mode;
    }

    public String name() {
      return name;
    }

    public String getName() {
      return name;
    }

    public String policy() {
      return policy;
    }

    public String getPolicy() {
      return policy;
    }

    public String token() {
      return token;
    }

    public String getToken() {
      return token;
    }

    public String mode() {
      return mode;
    }

    public String getMode() {
      return mode;
    }

    @Override
    public boolean equals(Object other) {
      if (this == other) return true;
      if (other == null || getClass() != other.getClass()) return false;
      ClaimRequest that = (ClaimRequest) other;
      return java.util.Objects.equals(name, that.name)
          && java.util.Objects.equals(policy, that.policy)
          && java.util.Objects.equals(token, that.token)
          && java.util.Objects.equals(mode, that.mode);
    }

    @Override
    public int hashCode() {
      return java.util.Objects.hash(name, policy, token, mode);
    }
  }

  @LogPayload
  static final class ClaimResponse {
    private final @LogMask String name;
    private final @LogMask(strategy = MaskStrategy.LAST_FOUR) String policy;

    @com.fasterxml.jackson.annotation.JsonCreator
    public ClaimResponse(
        @com.fasterxml.jackson.annotation.JsonProperty("name") String name,
        @com.fasterxml.jackson.annotation.JsonProperty("policy") String policy) {

      this.name = name;
      this.policy = policy;
    }

    public String name() {
      return name;
    }

    public String getName() {
      return name;
    }

    public String policy() {
      return policy;
    }

    public String getPolicy() {
      return policy;
    }

    @Override
    public boolean equals(Object other) {
      if (this == other) return true;
      if (other == null || getClass() != other.getClass()) return false;
      ClaimResponse that = (ClaimResponse) other;
      return java.util.Objects.equals(name, that.name)
          && java.util.Objects.equals(policy, that.policy);
    }

    @Override
    public int hashCode() {
      return java.util.Objects.hash(name, policy);
    }
  }

  @LogPayload
  static final class ErrorResponse {
    private final @LogValue String code;
    private final @LogValue String traceId;

    @com.fasterxml.jackson.annotation.JsonCreator
    public ErrorResponse(
        @com.fasterxml.jackson.annotation.JsonProperty("code") String code,
        @com.fasterxml.jackson.annotation.JsonProperty("traceId") String traceId) {

      this.code = code;
      this.traceId = traceId;
    }

    public String code() {
      return code;
    }

    public String getCode() {
      return code;
    }

    public String traceId() {
      return traceId;
    }

    public String getTraceId() {
      return traceId;
    }

    @Override
    public boolean equals(Object other) {
      if (this == other) return true;
      if (other == null || getClass() != other.getClass()) return false;
      ErrorResponse that = (ErrorResponse) other;
      return java.util.Objects.equals(code, that.code)
          && java.util.Objects.equals(traceId, that.traceId);
    }

    @Override
    public int hashCode() {
      return java.util.Objects.hash(code, traceId);
    }
  }

  @RestController
  static class ClaimController {
    final ClaimService service;

    ClaimController(ClaimService service) {
      this.service = service;
    }

    @PostMapping("/claims")
    ClaimResponse submit(@Valid @RequestBody ClaimRequest request) {
      return service.submit(request);
    }

    @PostMapping("/other")
    ClaimResponse other(@RequestBody ClaimRequest request) {
      return new ClaimResponse(request.name(), request.policy());
    }
  }

  public static class ClaimService {
    final DependencyLogging dependency;
    final RestTemplate client;

    ClaimService(DependencyLogging dependency, RestTemplate client) {
      this.dependency = dependency;
      this.client = client;
    }

    @LoggedOperation("claim.submit")
    public ClaimResponse submit(ClaimRequest request) {
      if ("npe".equals(request.mode()))
        throw new NullPointerException("sensitive-exception-message");
      if ("dependency".equals(request.mode()))
        dependency.call(
            "policy.lookup",
            request,
            () -> client.getForObject("https://policy.invalid/lookup", String.class));
      return new ClaimResponse(request.name(), request.policy());
    }
  }

  // Represents the consumer's EXISTING handler. The library installs no response mapper.
  @RestControllerAdvice
  static class ExistingHandler {
    final ExceptionLoggingBridge bridge;

    ExistingHandler(ExceptionLoggingBridge bridge) {
      this.bridge = bridge;
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> handle(Exception e) {
      bridge.log(e);
      boolean invalid =
          e instanceof MethodArgumentNotValidException
              || e instanceof org.springframework.http.converter.HttpMessageNotReadableException;
      boolean timeout = e.getCause() instanceof SocketTimeoutException;
      return ResponseEntity.status(invalid ? 400 : timeout ? 504 : 500)
          .body(
              new ErrorResponse(
                  invalid ? "INVALID_INPUT" : timeout ? "POLICY_TIMEOUT" : "INTERNAL_ERROR",
                  LogContext.traceId()));
    }
  }
}
