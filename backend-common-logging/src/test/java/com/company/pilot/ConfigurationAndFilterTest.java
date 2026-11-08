package com.company.pilot;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.*;
import io.github.commonlogging.api.*;
import io.github.commonlogging.internal.*;
import io.github.commonlogging.payload.*;
import io.github.commonlogging.spring.*;
import java.util.*;
import javax.servlet.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.web.*;

class ConfigurationAndFilterTest {
  final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(LoggingAutoConfiguration.class));

  @Test
  void defaultWiringDoesNotInstallHttpResponseHandlerOrApplicationMapper() {
    runner.run(
        ctx -> {
          assertThat(ctx)
              .hasSingleBean(TLog.class)
              .hasSingleBean(TExceptionLog.class)
              .hasSingleBean(DependencyLogging.class);
          assertThat(ctx).doesNotHaveBean(ObjectMapper.class);
          assertThat(ctx.getBean(LogOptions.class).payloadEnabled()).isFalse();
        });
  }

  @Test
  void consumerClassifierOverridesDefaultAndMissingAopDoesNotBreakCore() {
    ErrorClassifier custom = e -> new ErrorDetails("BUSINESS", "CUSTOM_CODE", true);
    runner
        .withBean(ErrorClassifier.class, () -> custom)
        .withClassLoader(new FilteredClassLoader("org.aspectj"))
        .run(
            ctx -> {
              assertThat(ctx).hasSingleBean(TLog.class);
              assertThat(ctx.getBean(ErrorClassifier.class)).isSameAs(custom);
              assertThat(ctx.containsBean("loggedOperationAspect")).isFalse();
            });
  }

  @Test
  void invalidLimitsFailStartup() {
    runner
        .withPropertyValues("backend.common.logging.max-depth=0")
        .run(ctx -> assertThat(ctx).hasFailed());
  }

  @Test
  void defaultClassifierDistinguishesWrappedTransportFailures() {
    runner.run(
        ctx -> {
          io.github.commonlogging.api.ErrorClassifier classifier =
              ctx.getBean(ErrorClassifier.class);
          assertThat(
                  classifier
                      .classify(new RuntimeException(new java.net.SocketTimeoutException()))
                      .category())
              .isEqualTo("DEPENDENCY_TIMEOUT");
          assertThat(classifier.classify(new java.net.ConnectException()).category())
              .isEqualTo("DEPENDENCY_UNAVAILABLE");
        });
  }

  @Test
  void asyncCompletesOnceAndNeverRetainsThreadContext() throws Exception {
    List<JsonNode> events = new ArrayList<>();
    io.github.commonlogging.spring.TraceFilter filter = filter(events);
    org.springframework.mock.web.MockHttpServletRequest request =
        new MockHttpServletRequest("GET", "/private/path");
    request.setAsyncSupported(true);
    request.addHeader("traceparent", TraceFixtures.header("f4fa978fb84c29446417377aaeb6de68"));
    org.springframework.mock.web.MockHttpServletResponse response = new MockHttpServletResponse();
    filter.doFilter(request, response, (req, res) -> req.startAsync(req, res));
    assertThat(events).hasSize(1);
    assertThat(LogContext.traceId()).isNull();
    request.getAsyncContext().complete();
    assertThat(events).hasSize(2);
    assertThat(events.get(1).path("event").asText()).isEqualTo("REQUEST_END");
    assertThat(events.get(1).path("spanId")).isEqualTo(events.get(0).path("spanId"));
    assertThat(events.get(0).path("parentSpanId").asText())
        .isEqualTo(
            TraceParent.parse(request.getHeader("traceparent"))
                .orElseThrow(java.util.NoSuchElementException::new)
                .spanId());
    assertThat(events.get(1).path("traceId").asText())
        .isEqualTo("f4fa978fb84c29446417377aaeb6de68");
    request.setDispatcherType(DispatcherType.ERROR);
    filter.doFilter(request, response, (req, res) -> {});
    assertThat(events).hasSize(2);
    assertThat(LogContext.traceId()).isNull();
    assertThat(events.toString()).doesNotContain("private/path");
  }

  @Test
  void escapedFilterExceptionIsFailedWithoutClaimingDefault200WasFinal() throws Exception {
    List<JsonNode> events = new ArrayList<>();
    io.github.commonlogging.spring.TraceFilter filter = filter(events);
    org.springframework.mock.web.MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("traceparent", "one");
    request.addHeader("traceparent", "two");
    org.springframework.mock.web.MockHttpServletResponse response = new MockHttpServletResponse();
    ServletException original = new ServletException("sensitive");
    assertThatThrownBy(
            () ->
                filter.doFilter(
                    request,
                    response,
                    (req, res) -> {
                      throw original;
                    }))
        .isSameAs(original);
    assertThat(events.get(1).path("outcome").asText()).isEqualTo("FAILED");
    assertThat(events.get(1).has("httpStatus")).isFalse();
    assertThat(response.getHeader("X-Trace-ID")).isNotIn("one", "two");
    assertThat(LogContext.traceId()).isNull();
  }

  TraceFilter filter(List<JsonNode> events) {
    ObjectMapper mapper = new ObjectMapper();
    io.github.commonlogging.spring.LoggingProperties p = new LoggingProperties();
    io.github.commonlogging.internal.StructuredEventWriter w =
        new StructuredEventWriter(
            (l, j) -> {
              try {
                events.add(mapper.readTree(j));
              } catch (Exception ex) {
                throw new IllegalStateException(ex);
              }
            },
            new LogOptions("test", "test", true, false, 8, "com.company"),
            new PayloadLogSerializer(new PayloadPolicies(), 3, 5, 100, 1024));
    return new TraceFilter(w, p);
  }
}
