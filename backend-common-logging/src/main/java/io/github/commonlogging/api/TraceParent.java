package io.github.commonlogging.api;

import io.opentelemetry.api.trace.*;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import java.util.*;

/** Compatibility value object delegating format validation and propagation to OTel. */
public record TraceParent(String traceId, String spanId, String flags) {
  public TraceParent {
    if (!validTraceId(traceId)
        || !validSpanId(spanId)
        || flags == null
        || !flags.matches("[0-9a-f]{2}"))
      throw new IllegalArgumentException("Invalid trace context");
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
    Map<String, String> carrier = Map.of("traceparent", header);
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
