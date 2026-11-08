package com.company.pilot;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.*;
import io.github.commonlogging.annotation.*;
import io.github.commonlogging.api.*;
import io.github.commonlogging.internal.*;
import io.github.commonlogging.payload.*;
import java.util.*;
import org.junit.jupiter.api.Test;

/** Second consumer: no controller, servlet request, or insurance DTO dependency. */
class JobConsumerTest {
  @LogPayload
  record JobInput(@LogValue String batch, @LogExclude String credential) {}

  @Test
  void standaloneJobUsesSameFacadesAndPreservesFailureContext() {
    List<JsonNode> events = new ArrayList<>();
    ObjectMapper mapper = new ObjectMapper();
    var writer =
        new StructuredEventWriter(
            (l, j) -> {
              try {
                events.add(mapper.readTree(j));
              } catch (Exception e) {
                throw new IllegalStateException(e);
              }
            },
            new LogOptions("batch-worker", "test", true, true, 16, "com.company"),
            new PayloadLogSerializer(new PayloadPolicies(), 3, 5, 100, 1024));
    var tLog = new TLog(writer);
    var tExceptionLog =
        new TExceptionLog(writer, e -> new ErrorDetails("INTERNAL", "JOB_ERROR", false));
    try (var job = tLog.withTrace("026ab639c21df8aa80e5789370a9db1b")) {
      try (var operation = tLog.start("batch.process", new JobInput("batch-1", "worker-secret"))) {
        try {
          throw new IllegalStateException("sensitive details");
        } catch (RuntimeException failure) {
          operation.fail(failure);
          tExceptionLog.error(operation.context(), failure);
        }
      }
    }
    assertThat(events).hasSize(3);
    assertThat(events)
        .allSatisfy(
            e ->
                assertThat(e.path("traceId").asText())
                    .isEqualTo("026ab639c21df8aa80e5789370a9db1b"));
    assertThat(events.toString()).doesNotContain("worker-secret", "sensitive details");
    assertThat(tLog.traceId()).isNull();
  }

  @Test
  void standaloneExceptionAlwaysGetsTraceId() {
    List<String> output = new ArrayList<>();
    var writer =
        new StructuredEventWriter(
            (l, j) -> output.add(j),
            new LogOptions("job", "test", true, false, 8, "com.company"),
            new PayloadLogSerializer(new PayloadPolicies(), 3, 5, 100, 1024));
    new TExceptionLog(writer, e -> new ErrorDetails("INTERNAL", "BUG", false))
        .error("job", new RuntimeException());
    assertThat(output.get(0)).contains("traceId");
    assertThat(LogContext.traceId()).isNull();
  }
}
