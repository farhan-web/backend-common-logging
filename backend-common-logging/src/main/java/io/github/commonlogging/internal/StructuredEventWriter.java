package io.github.commonlogging.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.commonlogging.api.*;
import io.github.commonlogging.payload.*;
import java.time.Instant;
import java.util.*;

public final class StructuredEventWriter {
  private final ObjectMapper mapper = new ObjectMapper();
  private final EventSink sink;
  private final LogOptions options;
  private final PayloadLogSerializer payloads;
  private final LoggingHealth health;

  public StructuredEventWriter(EventSink sink, LogOptions options, PayloadLogSerializer payloads) {
    this(sink, options, payloads, new LoggingHealth());
  }

  public StructuredEventWriter(
      EventSink sink, LogOptions options, PayloadLogSerializer payloads, LoggingHealth health) {
    this.health = health;
    this.sink = sink;
    this.options = options;
    this.payloads = payloads;
  }

  public LogOptions options() {
    return options;
  }

  public Map<String, Object> event(String type, FailureContext context) {
    Map<String, Object> e = new LinkedHashMap<>();
    e.put("schemaVersion", 3);
    e.put("timestamp", Instant.now().toString());
    e.put("event", type);
    e.put("application", options.application());
    e.put("environment", options.environment());
    String trace =
        context != null && context.traceId() != null ? context.traceId() : LogContext.traceId();
    if (trace != null) e.put("traceId", trace);
    if (context != null) {
      put(e, "operation", context.operation());
      put(e, "dependency", context.dependency());
      put(e, "spanKind", context.spanKind());
      put(e, "spanId", context.spanId());
      put(e, "parentSpanId", context.parentSpanId());
      if (context.location() != null) {
        put(e, "className", context.location().className());
        put(e, "methodName", context.location().methodName());
      }
    }
    return e;
  }

  public void payload(Map<String, Object> event, Object dto) {
    if (!options.enabled() || !options.payloadEnabled()) return;
    PayloadResult p = payloads.serialize(dto);
    if ("FAILED".equals(p.status())) health.payloadFailure();
    event.put("payload", p.payload());
    event.put("payloadStatus", p.status());
    if (dto != null) event.put("payloadType", dto.getClass().getName());
    put(event, "payloadReason", p.reason());
  }

  public void emit(String level, Map<String, Object> event) {
    if (!options.enabled()) return;
    try {
      event.put("level", level);
      sink.writeEvent(level, event, mapper);
      health.written();
    } catch (Exception ignored) {
      health.dropped(); // Never replace the business result or emit unsafe fallback data.
    }
  }

  public static void put(Map<String, Object> event, String key, Object value) {
    if (value != null) event.put(key, value);
  }

  public static String safe(String message) {
    return message == null ? null : message.substring(0, Math.min(512, message.length()));
  }
}
