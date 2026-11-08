package io.github.commonlogging.api;

import io.github.commonlogging.internal.LogContext;

public record FailureContext(
    String operation,
    String spanId,
    String parentSpanId,
    LogLocation location,
    String traceId,
    String dependency,
    String spanKind) {
  public FailureContext(
      String operation, String spanId, String parentSpanId, LogLocation location) {
    this(operation, spanId, parentSpanId, location, LogContext.traceId(), null, "INTERNAL");
  }

  public FailureContext(
      String operation, String spanId, String parentSpanId, LogLocation location, String traceId) {
    this(operation, spanId, parentSpanId, location, traceId, null, "INTERNAL");
  }

  public FailureContext(
      String operation,
      String spanId,
      String parentSpanId,
      LogLocation location,
      String traceId,
      String dependency) {
    this(
        operation,
        spanId,
        parentSpanId,
        location,
        traceId,
        dependency,
        dependency == null ? "INTERNAL" : "CLIENT");
  }
}
