package com.company.pilot;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.*;
import io.github.commonlogging.api.*;
import io.github.commonlogging.internal.*;
import io.github.commonlogging.payload.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.slf4j.MDC;

class CoreLoggingTest {
  final List<JsonNode> events = new CopyOnWriteArrayList<>();
  final ObjectMapper mapper = new ObjectMapper();

  StructuredEventWriter writer(boolean enabled) {
    return new StructuredEventWriter(
        (level, json) -> {
          try {
            events.add(mapper.readTree(json));
          } catch (Exception ex) {
            throw new IllegalStateException(ex);
          }
        },
        new LogOptions("test", "test", enabled, true, 64, "com.company"),
        new PayloadLogSerializer(new PayloadPolicies(), 5, 20, 128, 4096));
  }

  @AfterEach
  void clean() {
    assertThat(LogContext.traceId()).isNull();
    assertThat(MDC.get("traceId")).isNull();
  }

  @Test
  void nestedLifecycleAndAutomaticCaller() {
    TLog log = new TLog(writer(true));
    try (var request = LogContext.open("19f1064b619d49d35392eac7261cd726")) {
      try (var parent = log.start("claim.submit")) {
        try (var child = log.start("policy.lookup")) {
          child.end();
          child.end();
        }
        log.info("claim.ready", "ready");
        parent.end();
      }
      assertThat(LogContext.operation()).isNull();
    }
    assertThat(events).hasSize(5);
    assertThat(events.get(0).path("className").asText()).isEqualTo(getClass().getName());
    assertThat(events.get(0).path("methodName").asText())
        .isEqualTo("nestedLifecycleAndAutomaticCaller");
    assertThat(events.get(1).path("parentSpanId").asText())
        .isEqualTo(events.get(0).path("spanId").asText());
    assertThat(events.get(3).path("operation").asText()).isEqualTo("claim.submit");
    assertThat(events.get(3).path("step").asText()).isEqualTo("claim.ready");
    assertThat(events.get(3).path("spanId")).isEqualTo(events.get(0).path("spanId"));
    assertThat(events)
        .allSatisfy(
            e ->
                assertThat(e.path("traceId").asText())
                    .isEqualTo("19f1064b619d49d35392eac7261cd726"));
  }

  @Test
  void failureContextSurvivesOperationUnwindAndNeverPrintsExceptionMessage() {
    var w = writer(true);
    TLog log = new TLog(w);
    TExceptionLog errors = new TExceptionLog(w, e -> new ErrorDetails("INTERNAL", "BUG", false));
    RuntimeException cause;
    try {
      throw new NullPointerException("secret-token=must-not-appear");
    } catch (RuntimeException e) {
      cause = e;
    }
    RuntimeException wrapped = new IllegalStateException("private medical data", cause);
    try (var request = LogContext.open("6fc63565247226593c49a36e9da488e6")) {
      try (var operation = log.start("claim.submit")) {
        operation.fail(wrapped);
      }
      errors.error("request", wrapped);
      LogContext.recovered(wrapped);
      assertThat(LogContext.failure(wrapped)).isNull();
    }
    assertThat(events)
        .extracting(e -> e.path("event").asText())
        .containsExactly("PROCESS_START", "PROCESS_FAILED", "UNHANDLED_ERROR");
    JsonNode error = events.get(2);
    assertThat(error.path("operation").asText()).isEqualTo("claim.submit");
    assertThat(error.path("stackTrace")).hasSize(2);
    assertThat(error.path("errorOrigin").path("className").asText())
        .isEqualTo(getClass().getName());
    assertThat(error.toString()).doesNotContain("secret-token", "private medical data");
    assertThat(events.stream().filter(e -> e.has("stackTrace"))).hasSize(1);
  }

  @Test
  void omittedTerminalIsNotSuccessAndCloseIsIdempotent() {
    TLog log = new TLog(writer(true));
    var operation = log.start("claim.submit");
    operation.close();
    operation.close();
    assertThat(events).hasSize(2);
    assertThat(events.get(1).path("event").asText()).isEqualTo("PROCESS_INCOMPLETE");
  }

  @Test
  void disabledAndThrowingSinkDoNotAffectResult() {
    TLog disabled = new TLog(writer(false));
    try (var op = disabled.start("test")) {
      op.end();
    }
    assertThat(events).isEmpty();
    var w =
        new StructuredEventWriter(
            (l, j) -> {
              throw new IllegalStateException("sink failed");
            },
            new LogOptions("test", "test", true, true, 8, "com.company"),
            new PayloadLogSerializer(new PayloadPolicies(), 2, 2, 8, 128));
    assertThatCode(
            () -> {
              try (var op = new TLog(w).start("test")) {
                op.end();
              }
            })
        .doesNotThrowAnyException();
  }

  @Test
  void contextPropagationAndThreadReuse() throws Exception {
    ExecutorService pool = Executors.newSingleThreadExecutor();
    try {
      Runnable captured;
      try (var request = LogContext.open("e6a6f774877c2886df0b4d0442ac80e4")) {
        captured =
            LogContext.wrap(
                () ->
                    assertThat(LogContext.traceId()).isEqualTo("e6a6f774877c2886df0b4d0442ac80e4"));
      }
      pool.submit(captured).get();
      assertThat(pool.submit(LogContext::traceId).get()).isNull();
      List<Future<?>> jobs = new ArrayList<>();
      for (int i = 0; i < 20; i++) {
        String id = TraceFixtures.id("id-" + i);
        jobs.add(
            pool.submit(
                () -> {
                  try (var scope = LogContext.open(id)) {
                    assertThat(LogContext.traceId()).isEqualTo(id);
                  }
                }));
      }
      for (Future<?> job : jobs) job.get();
      assertThat(pool.submit(LogContext::traceId).get()).isNull();
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void rootCauseOriginSurvivesWrapperFrameBudget() {
    var w = writer(true);
    var cause = new NullPointerException("private-data");
    cause.setStackTrace(
        new StackTraceElement[] {
          new StackTraceElement("com.company.Service", "validate", "Service.java", 87)
        });
    var wrapper = new RuntimeException(cause);
    StackTraceElement[] wrapperFrames = new StackTraceElement[100];
    Arrays.fill(
        wrapperFrames, new StackTraceElement("vendor.Adapter", "invoke", "Adapter.java", 1));
    wrapper.setStackTrace(wrapperFrames);
    new TExceptionLog(w, e -> new ErrorDetails("INTERNAL", "BUG", false)).error("test", wrapper);
    assertThat(events.get(0).path("errorOrigin").path("methodName").asText()).isEqualTo("validate");
    assertThat(events.get(0).path("stackTraceTruncated").asBoolean()).isTrue();
  }

  @Test
  void expectedRejectionHasNoStackTrace() {
    var errors =
        new TExceptionLog(writer(true), e -> new ErrorDetails("BUSINESS", "CLAIM_REJECTED", true));
    try (var request = LogContext.open("084e990a49ba65e622926853785e0ed0")) {
      errors.error("claim.submit", new IllegalArgumentException("PII"));
    }
    assertThat(events.get(0).path("event").asText()).isEqualTo("PROCESS_REJECTED");
    assertThat(events.get(0).has("stackTrace")).isFalse();
  }
}
