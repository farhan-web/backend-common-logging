package io.github.commonlogging.api;

import io.github.commonlogging.internal.LogContext;

public final class FailureContext {
  private final String operation;
  private final String spanId;
  private final String parentSpanId;
  private final LogLocation location;
  private final String traceId;
  private final String dependency;
  private final String spanKind;

  @com.fasterxml.jackson.annotation.JsonCreator
  public FailureContext(
      @com.fasterxml.jackson.annotation.JsonProperty("operation") String operation,
      @com.fasterxml.jackson.annotation.JsonProperty("spanId") String spanId,
      @com.fasterxml.jackson.annotation.JsonProperty("parentSpanId") String parentSpanId,
      @com.fasterxml.jackson.annotation.JsonProperty("location") LogLocation location,
      @com.fasterxml.jackson.annotation.JsonProperty("traceId") String traceId,
      @com.fasterxml.jackson.annotation.JsonProperty("dependency") String dependency,
      @com.fasterxml.jackson.annotation.JsonProperty("spanKind") String spanKind) {

    this.operation = operation;
    this.spanId = spanId;
    this.parentSpanId = parentSpanId;
    this.location = location;
    this.traceId = traceId;
    this.dependency = dependency;
    this.spanKind = spanKind;
  }

  public String operation() {
    return operation;
  }

  public String getOperation() {
    return operation;
  }

  public String spanId() {
    return spanId;
  }

  public String getSpanId() {
    return spanId;
  }

  public String parentSpanId() {
    return parentSpanId;
  }

  public String getParentSpanId() {
    return parentSpanId;
  }

  public LogLocation location() {
    return location;
  }

  public LogLocation getLocation() {
    return location;
  }

  public String traceId() {
    return traceId;
  }

  public String getTraceId() {
    return traceId;
  }

  public String dependency() {
    return dependency;
  }

  public String getDependency() {
    return dependency;
  }

  public String spanKind() {
    return spanKind;
  }

  public String getSpanKind() {
    return spanKind;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) return true;
    if (other == null || getClass() != other.getClass()) return false;
    FailureContext that = (FailureContext) other;
    return java.util.Objects.equals(operation, that.operation)
        && java.util.Objects.equals(spanId, that.spanId)
        && java.util.Objects.equals(parentSpanId, that.parentSpanId)
        && java.util.Objects.equals(location, that.location)
        && java.util.Objects.equals(traceId, that.traceId)
        && java.util.Objects.equals(dependency, that.dependency)
        && java.util.Objects.equals(spanKind, that.spanKind);
  }

  @Override
  public int hashCode() {
    return java.util.Objects.hash(
        operation, spanId, parentSpanId, location, traceId, dependency, spanKind);
  }

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
