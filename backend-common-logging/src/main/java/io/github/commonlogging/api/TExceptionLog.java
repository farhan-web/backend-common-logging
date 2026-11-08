package io.github.commonlogging.api;

import io.github.commonlogging.internal.*;
import java.util.*;

public final class TExceptionLog {
  private final StructuredEventWriter writer;
  private final ErrorClassifier classifier;
  private final TracingRuntime runtime;
  private final CallerLocationResolver caller = new CallerLocationResolver();

  public TExceptionLog(StructuredEventWriter writer, ErrorClassifier classifier) {
    this(writer, classifier, TracingRuntime.standalone());
  }

  public TExceptionLog(
      StructuredEventWriter writer, ErrorClassifier classifier, TracingRuntime runtime) {
    this.runtime = runtime;
    this.writer = writer;
    this.classifier = classifier;
  }

  public void error(String operation, Throwable cause) {
    FailureContext ctx = LogContext.failure(cause);
    if (ctx == null) ctx = new FailureContext(operation, null, null, caller.resolve());
    error(ctx, cause);
  }

  public void error(FailureContext context, Throwable cause) {
    Objects.requireNonNull(cause);
    if (context == null) context = LogContext.operation();
    if (context == null
        || !TraceParent.validTraceId(context.traceId())
        || !TraceParent.validSpanId(context.spanId())) {
      try (io.github.commonlogging.internal.LogContext.SpanScope span =
          LogContext.start(
              LogContext.runtime(runtime),
              context == null ? "log.error" : context.operation(),
              "INTERNAL",
              context == null ? caller.resolve() : context.location(),
              null)) {
        span.error(cause.getClass().getName());
        write(span.context(), cause);
      }
    } else write(context, cause);
  }

  private void write(FailureContext context, Throwable cause) {
    ErrorDetails details;
    try {
      details = Objects.requireNonNull(classifier.classify(cause));
    } catch (RuntimeException ignored) {
      details = new ErrorDetails("INTERNAL", "UNEXPECTED_ERROR", false);
    }
    java.util.Map<java.lang.String, java.lang.Object> e =
        writer.event(details.expected() ? "PROCESS_REJECTED" : "UNHANDLED_ERROR", context);
    e.put("errorCategory", details.category());
    e.put("errorCode", details.code());
    e.put("exceptionType", cause.getClass().getName());
    e.put("outcome", details.expected() ? "REJECTED" : "FAILED");
    // Exception messages are deliberately omitted: may contain PII/SQL/tokens.
    if (!details.expected()) {
      List<Map<String, Object>> causes = new ArrayList<>();
      Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
      Map<String, Object> origin = null;
      int remaining = writer.options().maxFrames();
      boolean truncated = false;
      for (Throwable t = cause; t != null && seen.add(t) && causes.size() < 8; t = t.getCause()) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("exceptionType", t.getClass().getName());
        List<String> frames = new ArrayList<>();
        Map<String, Object> localOrigin = null;
        for (StackTraceElement frame : t.getStackTrace()) {
          if (remaining > 0) {
            remaining--;
            frames.add(frame.toString());
          } else {
            truncated = true;
          }
          if (localOrigin == null && applicationFrame(frame)) {
            localOrigin = new LinkedHashMap<>();
            localOrigin.put("className", frame.getClassName());
            localOrigin.put("methodName", frame.getMethodName());
            if (frame.getFileName() != null) localOrigin.put("fileName", frame.getFileName());
            if (frame.getLineNumber() >= 0) localOrigin.put("line", frame.getLineNumber());
          }
        }
        if (localOrigin != null) origin = localOrigin;
        item.put("frames", frames);
        causes.add(item);
      }
      e.put("stackTrace", causes);
      if (truncated) e.put("stackTraceTruncated", true);
      if (origin != null) e.put("errorOrigin", origin);
    }
    writer.emit(details.expected() ? "INFO" : "ERROR", e);
  }

  private boolean applicationFrame(StackTraceElement f) {
    String pkg = writer.options().applicationPackage();
    String name = f.getClassName();
    return !pkg.trim().isEmpty() && (name.equals(pkg) || name.startsWith(pkg + "."));
  }
}
