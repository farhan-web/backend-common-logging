package io.github.commonlogging.internal;

import io.github.commonlogging.api.*;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.*;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.*;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.*;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import java.util.*;

/**
 * Uses the application's standard OpenTelemetry instance; never registers a second global tracer.
 */
public final class TracingRuntime {
  private final OpenTelemetry telemetry;

  public TracingRuntime(OpenTelemetry telemetry) {
    this.telemetry = Objects.requireNonNull(telemetry);
  }

  public io.opentelemetry.api.trace.Tracer tracer() {
    return telemetry.getTracer("io.github.commonlogging", "0.1.0");
  }

  public OpenTelemetry telemetry() {
    return telemetry;
  }

  public Context extract(String header) {
    Map<String, String> carrier =
        header == null
            ? io.github.commonlogging.internal.Java8Collections.map()
            : io.github.commonlogging.internal.Java8Collections.map("traceparent", header);
    return W3CTraceContextPropagator.getInstance()
        .extract(
            Context.root(),
            carrier,
            new TextMapGetter<Map<String, String>>() {
              public Iterable<String> keys(Map<String, String> c) {
                return c.keySet();
              }

              public String get(Map<String, String> c, String key) {
                return c.get(key);
              }
            });
  }

  public String traceparent() {
    Map<String, String> headers = new HashMap<>();
    W3CTraceContextPropagator.getInstance().inject(Context.current(), headers, Map::put);
    return headers.get("traceparent");
  }

  public static OpenTelemetrySdk standaloneTelemetry() {
    return OpenTelemetrySdk.builder()
        .setTracerProvider(
            SdkTracerProvider.builder().setSampler(Sampler.parentBased(Sampler.alwaysOn())).build())
        .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
        .build();
  }

  public static TracingRuntime standalone() {
    return Holder.INSTANCE;
  }

  private static final class Holder {
    static final TracingRuntime INSTANCE = new TracingRuntime(standaloneTelemetry());
  }

  public static FailureContext context(
      Span span,
      String operation,
      LogLocation location,
      String dependency,
      String kind,
      String fallbackParent) {
    String parent = fallbackParent;
    if (span instanceof ReadableSpan && ((ReadableSpan) span).getParentSpanContext().isValid())
      parent = ((ReadableSpan) span).getParentSpanContext().getSpanId();
    SpanContext c = span.getSpanContext();
    return new FailureContext(
        operation, c.getSpanId(), parent, location, c.getTraceId(), dependency, kind);
  }
}
