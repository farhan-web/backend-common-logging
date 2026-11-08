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
  static final class JobInput {
    private final @LogValue String batch;
    private final @LogExclude String credential;

    @com.fasterxml.jackson.annotation.JsonCreator
    public JobInput(
        @com.fasterxml.jackson.annotation.JsonProperty("batch") String batch,
        @com.fasterxml.jackson.annotation.JsonProperty("credential") String credential) {

      this.batch = batch;
      this.credential = credential;
    }

    public String batch() {
      return batch;
    }

    public String getBatch() {
      return batch;
    }

    public String credential() {
      return credential;
    }

    public String getCredential() {
      return credential;
    }

    @Override
    public boolean equals(Object other) {
      if (this == other) return true;
      if (other == null || getClass() != other.getClass()) return false;
      JobInput that = (JobInput) other;
      return java.util.Objects.equals(batch, that.batch)
          && java.util.Objects.equals(credential, that.credential);
    }

    @Override
    public int hashCode() {
      return java.util.Objects.hash(batch, credential);
    }
  }

  @Test
  void standaloneJobUsesSameFacadesAndPreservesFailureContext() {
    List<JsonNode> events = new ArrayList<>();
    ObjectMapper mapper = new ObjectMapper();
    io.github.commonlogging.internal.StructuredEventWriter writer =
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
    io.github.commonlogging.api.TLog tLog = new TLog(writer);
    io.github.commonlogging.api.TExceptionLog tExceptionLog =
        new TExceptionLog(writer, e -> new ErrorDetails("INTERNAL", "JOB_ERROR", false));
    try (io.github.commonlogging.api.TraceScope job =
        tLog.withTrace("026ab639c21df8aa80e5789370a9db1b")) {
      try (io.github.commonlogging.api.LogOperation operation =
          tLog.start("batch.process", new JobInput("batch-1", "worker-secret"))) {
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
    io.github.commonlogging.internal.StructuredEventWriter writer =
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
