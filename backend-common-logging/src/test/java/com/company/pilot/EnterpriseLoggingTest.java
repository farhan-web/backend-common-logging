package com.company.pilot;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.*;
import io.github.commonlogging.annotation.*;
import io.github.commonlogging.api.*;
import io.github.commonlogging.internal.*;
import io.github.commonlogging.payload.*;
import io.github.commonlogging.spring.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.opentelemetry.api.trace.*;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.sdk.*;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.*;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.mock.web.*;

class EnterpriseLoggingTest {
  final ObjectMapper mapper = new ObjectMapper();
  final List<JsonNode> events = new ArrayList<>();
  final PayloadLogSerializer payloads =
      new PayloadLogSerializer(new PayloadPolicies(), 6, 20, 100, 4096);
  final LoggingHealth health = new LoggingHealth();
  final InMemorySpanExporter exporter = InMemorySpanExporter.create();
  final OpenTelemetrySdk sdk =
      OpenTelemetrySdk.builder()
          .setTracerProvider(
              SdkTracerProvider.builder()
                  .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                  .build())
          .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
          .build();
  final TracingRuntime runtime = new TracingRuntime(sdk);
  final StructuredEventWriter writer =
      new StructuredEventWriter(
          (level, json) -> {
            try {
              events.add(mapper.readTree(json));
            } catch (Exception ex) {
              throw new IllegalStateException(ex);
            }
          },
          new LogOptions("test", "test", true, true, 16, "com.company"),
          payloads,
          health);
  final TLog log = new TLog(writer, runtime);

  @AfterEach
  void close() {
    assertThat(LogContext.traceId()).isNull();
    assertThat(org.slf4j.MDC.get("traceId")).isNull();
    sdk.close();
  }

  @Test
  void frameworkSpanIsReusedAndChildrenAreActuallyExported() throws Exception {
    Span server =
        runtime.tracer().spanBuilder("framework.server").setSpanKind(SpanKind.SERVER).startSpan();
    try (io.opentelemetry.context.Scope scope = server.makeCurrent()) {
      new TraceFilter(writer, new LoggingProperties(), runtime)
          .doFilter(
              new MockHttpServletRequest(),
              new MockHttpServletResponse(),
              (req, res) -> {
                try (io.github.commonlogging.api.LogOperation operation = log.start("claim.save")) {
                  operation.end();
                }
              });
      assertThat(exporter.getFinishedSpanItems()).hasSize(1);
      assertThat(events.get(0).path("spanId").asText())
          .isEqualTo(server.getSpanContext().getSpanId());
    } finally {
      server.end();
    }
    java.util.List<io.opentelemetry.sdk.trace.data.SpanData> spans =
        exporter.getFinishedSpanItems();
    assertThat(spans).hasSize(2);
    io.opentelemetry.sdk.trace.data.SpanData child =
        spans.stream()
            .filter(s -> s.getName().equals("claim.save"))
            .findFirst()
            .orElseThrow(java.util.NoSuchElementException::new);
    assertThat(child.getParentSpanId()).isEqualTo(server.getSpanContext().getSpanId());
    assertThat(child.getTraceId()).isEqualTo(server.getSpanContext().getTraceId());
    assertThat(spans.stream().filter(s -> s.getKind() == SpanKind.SERVER)).hasSize(1);
  }

  @Test
  void errorSpansCarryTypeWithoutSensitiveExceptionMessage() {
    try (io.github.commonlogging.api.LogOperation op = log.start("claim.fail")) {
      op.fail(new NullPointerException("private medical record"));
    }
    io.opentelemetry.sdk.trace.data.SpanData span = exporter.getFinishedSpanItems().get(0);
    assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
    assertThat(span.getEvents()).isEmpty();
    assertThat(span.toString()).doesNotContain("private medical record");
    assertThat(events.toString()).doesNotContain("private medical record");
  }

  @LogPayload
  static final class Classified {
    private final @LogValue String product;
    private final String newlyAddedSecret;

    @com.fasterxml.jackson.annotation.JsonCreator
    public Classified(
        @com.fasterxml.jackson.annotation.JsonProperty("product") String product,
        @com.fasterxml.jackson.annotation.JsonProperty("newlyAddedSecret")
            String newlyAddedSecret) {

      this.product = product;
      this.newlyAddedSecret = newlyAddedSecret;
    }

    public String product() {
      return product;
    }

    public String getProduct() {
      return product;
    }

    public String newlyAddedSecret() {
      return newlyAddedSecret;
    }

    public String getNewlyAddedSecret() {
      return newlyAddedSecret;
    }

    @Override
    public boolean equals(Object other) {
      if (this == other) return true;
      if (other == null || getClass() != other.getClass()) return false;
      Classified that = (Classified) other;
      return java.util.Objects.equals(product, that.product)
          && java.util.Objects.equals(newlyAddedSecret, that.newlyAddedSecret);
    }

    @Override
    public int hashCode() {
      return java.util.Objects.hash(product, newlyAddedSecret);
    }
  }

  @Test
  void newUnclassifiedFieldIsAbsentAndDtoIsUnchanged() {
    com.company.pilot.EnterpriseLoggingTest.Classified dto =
        new Classified("motor", "private-value");
    io.github.commonlogging.payload.PayloadResult result = payloads.serialize(dto);
    assertThat(result.payload().path("product").asText()).isEqualTo("motor");
    assertThat(result.payload().has("newlyAddedSecret")).isFalse();
    assertThat(dto.newlyAddedSecret()).isEqualTo("private-value");
  }

  interface Contract {
    boolean apply(
        @LogMask String identity, @LogValue("approved") boolean approved, @LogExclude String token);
  }

  public static class Service implements Contract {
    @LoggedOperation("claim.parameters")
    @LogValue
    public boolean apply(
        @LogValue String identity, @LogValue boolean approved, @LogValue String token) {
      return approved;
    }
  }

  @Test
  void multipleScalarParametersRespectStricterInterfaceRules() {
    org.springframework.aop.aspectj.annotation.AspectJProxyFactory factory =
        new AspectJProxyFactory(new Service());
    factory.setProxyTargetClass(true);
    factory.addAspect(new LoggedOperationAspect(log, payloads));
    Service service = factory.getProxy();
    assertThat(service.apply("private-identity", true, "private-token")).isTrue();
    com.fasterxml.jackson.databind.JsonNode args = events.get(0).path("payload").path("arguments");
    assertThat(args).hasSize(2);
    assertThat(args.get(0).path("value").asText()).isEqualTo("***");
    assertThat(args.get(1).path("value").asBoolean()).isTrue();
    assertThat(events.get(1).path("payload").path("value").asBoolean()).isTrue();
    assertThat(events.toString()).doesNotContain("private-identity", "private-token");
  }

  @Test
  void sequentialJobOperationsStayInOneRealTrace() {
    String trace;
    try (io.github.commonlogging.api.TraceScope job = log.withTrace(null)) {
      trace = log.traceId();
      try (io.github.commonlogging.api.LogOperation first = log.start("job.first")) {
        first.end();
      }
      try (io.github.commonlogging.api.LogOperation second = log.start("job.second")) {
        second.end();
      }
    }
    assertThat(exporter.getFinishedSpanItems())
        .hasSize(3)
        .allSatisfy(span -> assertThat(span.getTraceId()).isEqualTo(trace));
  }

  interface GenericContract<T> {
    boolean check(@LogMask(strategy = MaskStrategy.EXCLUDE) T input);
  }

  public static class GenericService implements GenericContract<Classified> {
    @LoggedOperation("claim.generic")
    public boolean check(@LogValue Classified input) {
      return true;
    }

    public boolean check(String unrelated) {
      return false;
    }
  }

  @Test
  void genericBridgeCannotBypassExcludedParameterOrMatchAnUnrelatedOverload() {
    org.springframework.aop.aspectj.annotation.AspectJProxyFactory factory =
        new AspectJProxyFactory(new GenericService());
    factory.setProxyTargetClass(true);
    factory.addAspect(new LoggedOperationAspect(log, payloads));
    GenericService service = factory.getProxy();
    assertThat(service.check(new Classified("private-product", "private-secret"))).isTrue();
    assertThat(events.toString()).doesNotContain("private-product", "private-secret");
    assertThat(events.get(0).path("payload").path("arguments")).isEmpty();
  }

  interface ResultContract {
    @LogMask
    String result();
  }

  public static class ResultService implements ResultContract {
    @LoggedOperation("claim.result")
    @LogValue
    public String result() {
      return "private-result";
    }
  }

  @Test
  void interfaceResultMaskCannotBeWeakenedByImplementation() {
    org.springframework.aop.aspectj.annotation.AspectJProxyFactory factory =
        new AspectJProxyFactory(new ResultService());
    factory.setProxyTargetClass(true);
    factory.addAspect(new LoggedOperationAspect(log, payloads));
    ResultService service = factory.getProxy();
    assertThat(service.result()).isEqualTo("private-result");
    assertThat(events.get(1).path("payload").path("value").asText()).isEqualTo("***");
    assertThat(events.toString()).doesNotContain("private-result");
  }

  @Test
  void loggingFailureIncrementsBoundedMetricAndPreservesBusinessExecution() {
    io.micrometer.core.instrument.simple.SimpleMeterRegistry registry = new SimpleMeterRegistry();
    health.bindTo(registry);
    io.github.commonlogging.internal.StructuredEventWriter broken =
        new StructuredEventWriter(
            (level, json) -> {
              throw new IllegalStateException("sink unavailable");
            },
            writer.options(),
            payloads,
            health);
    try (io.github.commonlogging.api.LogOperation op =
        new TLog(broken, runtime).start("claim.save")) {
      op.end();
    }
    assertThat(health.droppedEvents()).isEqualTo(2);
    assertThat(registry.get("common.logging.events.dropped").functionCounter().count())
        .isEqualTo(2);
    registry.close();
  }
}
