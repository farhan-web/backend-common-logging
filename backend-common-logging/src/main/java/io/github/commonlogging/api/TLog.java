package io.github.commonlogging.api;

import io.github.commonlogging.internal.*;

public final class TLog {
  private final StructuredEventWriter writer;
  private final CallerLocationResolver caller = new CallerLocationResolver();
  private final TracingRuntime runtime;

  public TLog(StructuredEventWriter writer) {
    this(writer, TracingRuntime.standalone());
  }

  public TLog(StructuredEventWriter writer, TracingRuntime runtime) {
    this.writer = writer;
    this.runtime = runtime;
  }

  /** Job scope owns a real CONSUMER span, keeping sequential operations in one trace. */
  public TraceScope withTrace(String candidate) {
    LogContext.Scope seed = LogContext.open(candidate, "01", null, false, runtime);
    LogContext.SpanScope scope;
    try {
      scope = LogContext.start(runtime, "trace.scope", "CONSUMER", null, null);
    } catch (RuntimeException failure) {
      seed.close();
      throw failure;
    }
    return () -> {
      try {
        scope.close();
      } finally {
        seed.close();
      }
    };
  }

  /** Server/job ingress: the first started span becomes a child of the received remote span. */
  public TraceScope withTraceparent(String header) {
    io.github.commonlogging.internal.LogContext.Scope scope = LogContext.incoming(runtime, header);
    return scope::close;
  }

  public String traceId() {
    return LogContext.traceId();
  }

  public String traceparent() {
    return LogContext.traceparent();
  }

  public boolean isPayloadEnabled() {
    return writer.options().enabled() && writer.options().payloadEnabled();
  }

  public Runnable propagate(Runnable task) {
    return LogContext.wrap(task);
  }

  public void recovered(Throwable cause) {
    LogContext.recovered(cause);
  }

  public LogOperation start(String operation) {
    return startAt(operation, null, false, caller.resolve());
  }

  public LogOperation start(String operation, Object inputDto) {
    return startAt(operation, inputDto, true, caller.resolve());
  }

  public LogOperation startAt(
      String operation, Object inputDto, boolean capture, LogLocation location) {
    return startInternal(operation, inputDto, capture, location, null, "INTERNAL");
  }

  public LogOperation startDependency(
      String dependency, String operation, Object inputDto, LogLocation location) {
    if (dependency == null || !dependency.matches("[A-Za-z0-9_.-]{1,64}"))
      throw new IllegalArgumentException("Invalid dependency label");
    return startInternal(operation, inputDto, true, location, dependency, "CLIENT");
  }

  /** Logical HTTP integration scope. Boot's observed client creates the actual CLIENT span. */
  public LogOperation startHttpDependency(
      String dependency, String operation, Object inputDto, LogLocation location) {
    if (dependency == null || !dependency.matches("[A-Za-z0-9_.-]{1,64}"))
      throw new IllegalArgumentException("Invalid dependency label");
    return startInternal(operation, inputDto, true, location, dependency, "INTERNAL");
  }

  private LogOperation startInternal(
      String operation,
      Object inputDto,
      boolean capture,
      LogLocation location,
      String dependency,
      String kind) {
    if (operation == null || !operation.matches("[A-Za-z0-9_.-]{1,128}"))
      throw new IllegalArgumentException("Invalid operation label");
    TracingRuntime active = LogContext.runtime(runtime);
    io.github.commonlogging.internal.LogContext.SpanScope span =
        LogContext.start(
            active,
            operation,
            LogContext.serverEntry() && dependency == null ? "SERVER" : kind,
            location,
            dependency);
    return new LogOperation(writer, span, inputDto, capture);
  }

  public void info(String step, String safeMessage) {
    // A standalone event still gets a trace and a span; context is always cleaned afterward.
    if (LogContext.operation() == null) {
      try (io.github.commonlogging.api.LogOperation span =
          startAt("log.event", null, false, caller.resolve())) {
        info(step, safeMessage);
        span.end();
      }
      return;
    }
    java.util.Map<java.lang.String, java.lang.Object> e =
        writer.event("PROCESS_INFO", location(step));
    e.put("step", StructuredEventWriter.safe(step));
    e.put("message", StructuredEventWriter.safe(safeMessage));
    writer.emit("INFO", e);
  }

  public void payload(String event, Object dto, LogLocation location) {
    if (!io.github.commonlogging.internal.Java8Collections.set(
            "REQUEST_PAYLOAD",
            "RESPONSE_PAYLOAD",
            "DEPENDENCY_REQUEST_PAYLOAD",
            "DEPENDENCY_RESPONSE_PAYLOAD")
        .contains(event)) throw new IllegalArgumentException("Invalid payload event");
    if (!writer.options().payloadEnabled()) return;
    io.github.commonlogging.api.FailureContext active = LogContext.operation();
    if (active == null) {
      try (io.github.commonlogging.api.LogOperation span =
          startAt("log.payload", null, false, location)) {
        payload(event, dto, location);
        span.end();
      }
      return;
    }
    io.github.commonlogging.api.FailureContext context =
        new FailureContext(
            active.operation(),
            active.spanId(),
            active.parentSpanId(),
            location,
            active.traceId(),
            active.dependency(),
            active.spanKind());
    java.util.Map<java.lang.String, java.lang.Object> e = writer.event(event, context);
    writer.payload(e, dto);
    writer.emit("INFO", e);
  }

  private FailureContext location(String step) {
    io.github.commonlogging.api.FailureContext current = LogContext.operation();
    return new FailureContext(
        current.operation(),
        current.spanId(),
        current.parentSpanId(),
        caller.resolve(),
        current.traceId(),
        current.dependency(),
        current.spanKind());
  }
}
