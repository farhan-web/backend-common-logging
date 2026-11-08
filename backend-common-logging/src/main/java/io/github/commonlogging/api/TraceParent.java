package io.github.commonlogging.api;

import io.opentelemetry.api.trace.*;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import java.util.*;

/** Compatibility value object delegating format validation and propagation to OTel. */
public final class TraceParent {
  private final String traceId;
  private final String spanId;
  private final String flags;

  @com.fasterxml.jackson.annotation.JsonCreator
  public TraceParent(
      @com.fasterxml.jackson.annotation.JsonProperty("traceId") String traceId,
      @com.fasterxml.jackson.annotation.JsonProperty("spanId") String spanId,
      @com.fasterxml.jackson.annotation.JsonProperty("flags") String flags) {

    if (!validTraceId(traceId)
        || !validSpanId(spanId)
        || flags == null
        || !flags.matches("[0-9a-f]{2}"))
      throw new IllegalArgumentException("Invalid trace context");

    this.traceId = traceId;
    this.spanId = spanId;
    this.flags = flags;
  }

  public String traceId() {
    return traceId;
  }

  public String getTraceId() {
    return traceId;
  }

  public String spanId() {
    return spanId;
  }

  public String getSpanId() {
    return spanId;
  }

  public String flags() {
    return flags;
  }

  public String getFlags() {
    return flags;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) return true;
    if (other == null || getClass() != other.getClass()) return false;
    TraceParent that = (TraceParent) other;
    return java.util.Objects.equals(traceId, that.traceId)
        && java.util.Objects.equals(spanId, that.spanId)
        && java.util.Objects.equals(flags, that.flags);
  }

  @Override
  public int hashCode() {
    return java.util.Objects.hash(traceId, spanId, flags);
  }

  public String header() {
    Map<String, String> h = new HashMap<>();
    SpanContext c =
        SpanContext.create(traceId, spanId, TraceFlags.fromHex(flags, 0), TraceState.getDefault());
    W3CTraceContextPropagator.getInstance().inject(Context.root().with(Span.wrap(c)), h, Map::put);
    return h.get("traceparent");
  }

  public static Optional<TraceParent> parse(String header) {
    if (header == null || header.length() > 512) return Optional.empty();
    Map<String, String> carrier =
        io.github.commonlogging.internal.Java8Collections.map("traceparent", header);
    Context context =
        W3CTraceContextPropagator.getInstance()
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
    SpanContext c = Span.fromContext(context).getSpanContext();
    return c.isValid()
        ? Optional.of(new TraceParent(c.getTraceId(), c.getSpanId(), c.getTraceFlags().asHex()))
        : Optional.empty();
  }

  public static boolean validTraceId(String id) {
    return TraceId.isValid(id);
  }

  public static boolean validSpanId(String id) {
    return SpanId.isValid(id);
  }
}
