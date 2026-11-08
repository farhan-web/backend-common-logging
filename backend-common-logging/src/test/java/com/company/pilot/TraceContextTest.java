package com.company.pilot;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.*;
import io.github.commonlogging.annotation.*;
import io.github.commonlogging.api.*;
import io.github.commonlogging.internal.*;
import io.github.commonlogging.payload.*;
import io.github.commonlogging.spring.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.slf4j.MDC;
import org.springframework.http.*;
import org.springframework.mock.http.client.*;
import org.springframework.mock.web.*;

class TraceContextTest {
  final List<JsonNode> events = new ArrayList<>();
  final ObjectMapper mapper = new ObjectMapper();
  final LoggingProperties properties = new LoggingProperties();
  final StructuredEventWriter writer =
      new StructuredEventWriter(
          (l, j) -> {
            try {
              events.add(mapper.readTree(j));
            } catch (Exception e) {
              throw new AssertionError(e);
            }
          },
          new LogOptions("test", "test", true, true, 32, "com.company"),
          new PayloadLogSerializer(new PayloadPolicies(), 4, 10, 100, 4096));
  final TLog log = new TLog(writer);
  final TraceFilter filter = new TraceFilter(writer, properties);

  @AfterEach
  void cleanup() {
    assertThat(LogContext.traceId()).isNull();
    for (String key :
        io.github.commonlogging.internal.Java8Collections.list("traceId", "spanId", "parentSpanId"))
      assertThat(MDC.get(key)).isNull();
  }

  @LogPayload
  static final class Payload {
    private final @LogMask String name;

    @com.fasterxml.jackson.annotation.JsonCreator
    public Payload(@com.fasterxml.jackson.annotation.JsonProperty("name") String name) {

      this.name = name;
    }

    public String name() {
      return name;
    }

    public String getName() {
      return name;
    }

    @Override
    public boolean equals(Object other) {
      if (this == other) return true;
      if (other == null || getClass() != other.getClass()) return false;
      Payload that = (Payload) other;
      return java.util.Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
      return java.util.Objects.hash(name);
    }
  }

  @Test
  void rootPayloadServiceClientAndRemoteServerFormOneTraceTree() throws Exception {
    String upstream = TraceFixtures.header("upstream");
    TraceParent parsed =
        TraceParent.parse(upstream).orElseThrow(java.util.NoSuchElementException::new);
    org.springframework.mock.web.MockHttpServletRequest request =
        new MockHttpServletRequest("POST", "/claims");
    request.addHeader("traceparent", upstream);
    org.springframework.mock.web.MockHttpServletResponse response = new MockHttpServletResponse();
    filter.doFilter(
        request,
        response,
        (req, res) -> {
          FailureContext root = LogContext.operation();
          assertThat(root.spanKind()).isEqualTo("SERVER");
          assertThat(root.parentSpanId()).isEqualTo(parsed.spanId());
          assertThat(MDC.get("spanId")).isEqualTo(root.spanId());
          log.payload(
              "REQUEST_PAYLOAD",
              new Payload("secret"),
              new LogLocation("com.company.Controller", "submit"));
          try (io.github.commonlogging.api.LogOperation service = log.start("claim.submit")) {
            assertThat(service.context().parentSpanId()).isEqualTo(root.spanId());
            new DependencyLogging(log)
                .call(
                    "core",
                    "core.submit",
                    new Payload("secret"),
                    () -> {
                      FailureContext client = LogContext.operation();
                      assertThat(client.parentSpanId()).isEqualTo(service.context().spanId());
                      String outbound = log.traceparent();
                      assertThat(
                              TraceParent.parse(outbound)
                                  .orElseThrow(java.util.NoSuchElementException::new)
                                  .spanId())
                          .isEqualTo(client.spanId());
                      try (io.github.commonlogging.api.TraceScope remote =
                              log.withTraceparent(outbound);
                          io.github.commonlogging.api.LogOperation server =
                              log.start("remote.receive")) {
                        assertThat(server.context().spanId()).isNotEqualTo(client.spanId());
                        assertThat(server.context().parentSpanId()).isEqualTo(client.spanId());
                        assertThat(server.context().spanKind()).isEqualTo("SERVER");
                        assertThat(log.traceId()).isEqualTo(parsed.traceId());
                        server.end();
                      }
                      assertThat(LogContext.operation()).isEqualTo(client);
                      assertThat(MDC.get("spanId")).isEqualTo(client.spanId());
                      return new Payload("secret");
                    });
            service.end();
          }
          assertThat(LogContext.operation()).isEqualTo(root);
          assertThat(MDC.get("spanId")).isEqualTo(root.spanId());
          log.payload(
              "RESPONSE_PAYLOAD",
              new Payload("secret"),
              new LogLocation("com.company.Controller", "submit"));
        });
    String rootSpan = response.getHeader("X-Span-ID");
    assertThat(response.getHeader("X-Trace-ID")).isEqualTo(parsed.traceId());
    assertThat(
            events.stream()
                .filter(
                    e ->
                        io.github.commonlogging.internal.Java8Collections.set(
                                "REQUEST_START",
                                "REQUEST_END",
                                "REQUEST_PAYLOAD",
                                "RESPONSE_PAYLOAD")
                            .contains(e.path("event").asText())))
        .hasSize(4)
        .allSatisfy(
            e -> {
              assertThat(e.path("spanId").asText()).isEqualTo(rootSpan);
              assertThat(e.path("spanKind").asText()).isEqualTo("SERVER");
            });
    assertThat(events)
        .allSatisfy(
            e -> {
              assertThat(e.path("traceId").asText()).isEqualTo(parsed.traceId());
              assertThat(e.path("spanId").asText()).matches("[0-9a-f]{16}");
              assertThat(e.path("schemaVersion").asInt()).isEqualTo(3);
              assertThat(e.has("correlationId")).isFalse();
              assertThat(e.has("operationId")).isFalse();
              assertThat(e.has("parentOperationId")).isFalse();
            });
    assertThat(events.toString()).doesNotContain("secret");
  }

  @Test
  void normalRequestsGetDifferentTracesAndIgnoreDiagnosticTraceHeader() throws Exception {
    org.springframework.mock.web.MockHttpServletRequest a = new MockHttpServletRequest();
    a.addHeader("X-Trace-ID", "static-demo");
    org.springframework.mock.web.MockHttpServletResponse ar = new MockHttpServletResponse();
    org.springframework.mock.web.MockHttpServletRequest b = new MockHttpServletRequest();
    b.addHeader("X-Trace-ID", "static-demo");
    org.springframework.mock.web.MockHttpServletResponse br = new MockHttpServletResponse();
    filter.doFilter(a, ar, (q, s) -> {});
    filter.doFilter(b, br, (q, s) -> {});
    assertThat(ar.getHeader("X-Trace-ID"))
        .matches("[0-9a-f]{32}")
        .isNotEqualTo(br.getHeader("X-Trace-ID"));
    assertThat(events).allSatisfy(e -> assertThat(e.has("parentSpanId")).isFalse());
    assertThat(events.get(0).path("spanId")).isEqualTo(events.get(1).path("spanId"));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(
      strings = {
        "invalid",
        "00-00000000000000000000000000000000-00f067aa0ba902b7-01",
        "00-4bf92f3577b34da6a3ce929d0e0e4736-0000000000000000-01",
        "ff-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01",
        "00-4BF92F3577B34DA6A3CE929D0E0E4736-00f067aa0ba902b7-01",
        "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01-extra",
        "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-zz"
      })
  void malformedTraceparentStartsFreshWithoutRawHeaderLog(String header) throws Exception {
    assertThat(TraceParent.parse(header)).isEmpty();
    org.springframework.mock.web.MockHttpServletRequest request = new MockHttpServletRequest();
    if (header != null) request.addHeader("traceparent", header);
    org.springframework.mock.web.MockHttpServletResponse response = new MockHttpServletResponse();
    filter.doFilter(request, response, (q, s) -> {});
    assertThat(TraceParent.validTraceId(response.getHeader("X-Trace-ID"))).isTrue();
    assertThat(events.get(0).has("parentSpanId")).isFalse();
  }

  @Test
  void futureTraceparentVersionIsParsedAndForwardedAsKnownVersion() {
    io.github.commonlogging.api.TraceParent parent =
        TraceParent.parse("01-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01-extension")
            .orElseThrow(java.util.NoSuchElementException::new);
    assertThat(parent.header())
        .isEqualTo("00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01");
  }

  @Test
  void interceptorPropagatesOnlyToAllowedHostAndUsesCurrentClientSpan() throws Exception {
    properties.setPropagationHosts(
        io.github.commonlogging.internal.Java8Collections.list("core.test"));
    io.github.commonlogging.spring.TraceClientInterceptor interceptor =
        new TraceClientInterceptor(properties);
    try (io.github.commonlogging.api.TraceScope trace =
            log.withTraceparent(TraceFixtures.header("client"));
        io.github.commonlogging.api.LogOperation span =
            log.startDependency("core", "core.call", null, null)) {
      org.springframework.mock.http.client.MockClientHttpRequest allowed =
          new MockClientHttpRequest(HttpMethod.GET, java.net.URI.create("https://core.test/path"));
      interceptor.intercept(
          allowed,
          new byte[0],
          (req, body) -> new MockClientHttpResponse(new byte[0], HttpStatus.OK));
      assertThat(allowed.getHeaders().getFirst("traceparent")).isEqualTo(log.traceparent());
      org.springframework.mock.http.client.MockClientHttpRequest denied =
          new MockClientHttpRequest(HttpMethod.GET, java.net.URI.create("https://other.test/path"));
      interceptor.intercept(
          denied,
          new byte[0],
          (req, body) -> new MockClientHttpResponse(new byte[0], HttpStatus.OK));
      assertThat(denied.getHeaders().containsKey("traceparent")).isFalse();
      span.end();
    }
  }

  @Test
  void asyncSnapshotCreatesChildAndRestoresPriorWorkerMdcEvenOnFailure() throws Exception {
    ExecutorService pool = Executors.newSingleThreadExecutor();
    try {
      Runnable task;
      String traceId, spanId;
      try (io.github.commonlogging.api.TraceScope trace = log.withTrace(null);
          io.github.commonlogging.api.LogOperation parent = log.start("parent")) {
        traceId = log.traceId();
        spanId = parent.context().spanId();
        task =
            log.propagate(
                () -> {
                  try (io.github.commonlogging.api.LogOperation child = log.start("async.child")) {
                    assertThat(child.context().parentSpanId()).isEqualTo(spanId);
                    assertThat(log.traceId()).isEqualTo(traceId);
                    assertThat(MDC.get("spanId")).isEqualTo(child.context().spanId());
                    child.end();
                  }
                  throw new IllegalStateException("test");
                });
        parent.end();
      }
      pool.submit(() -> MDC.put("spanId", "prior-worker-context")).get();
      assertThatThrownBy(() -> pool.submit(task).get()).isInstanceOf(ExecutionException.class);
      assertThat(pool.submit(() -> MDC.get("spanId")).get()).isEqualTo("prior-worker-context");
      assertThat(pool.submit(LogContext::traceId).get()).isNull();
      pool.submit(() -> MDC.remove("spanId")).get();
    } finally {
      pool.shutdownNow();
    }
  }
}
