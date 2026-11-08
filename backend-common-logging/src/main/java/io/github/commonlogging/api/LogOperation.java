package io.github.commonlogging.api;

import io.github.commonlogging.internal.*;
import java.util.*;

/** Synchronous thread-confined lifecycle; terminal calls are idempotent. */
public final class LogOperation implements AutoCloseable {
  private final StructuredEventWriter writer;
  private final FailureContext context;
  private final LogContext.SpanScope span;
  private final long started = System.nanoTime();
  private boolean terminal, closed;

  LogOperation(
      StructuredEventWriter writer, LogContext.SpanScope span, Object input, boolean capture) {
    this.writer = writer;
    this.span = span;
    this.context = span.context();
    java.util.Map<java.lang.String, java.lang.Object> e = writer.event(type("START"), context);
    if (capture) writer.payload(e, input);
    writer.emit("INFO", e);
  }

  public FailureContext context() {
    return context;
  }

  public void end() {
    endInternal(null, false);
  }

  public void end(Object output) {
    endInternal(output, true);
  }

  private void endInternal(Object output, boolean capture) {
    java.util.Map<java.lang.String, java.lang.Object> e = finish("END", "SUCCESS");
    if (e == null) return;
    if (capture) writer.payload(e, output);
    writer.emit("INFO", e);
  }

  public void reject(String code, String safeMessage) {
    java.util.Map<java.lang.String, java.lang.Object> e = finish("REJECTED", "REJECTED");
    if (e == null) return;
    e.put("errorCode", StructuredEventWriter.safe(code));
    e.put("message", StructuredEventWriter.safe(safeMessage));
    writer.emit("INFO", e);
  }

  public void fail(Throwable error) {
    fail(error, null);
  }

  public void fail(Throwable error, ErrorDetails details) {
    if (terminal || closed) return;
    LogContext.failed(error, context);
    boolean expected = details != null && details.expected();
    if (!expected) span.error(error.getClass().getName());
    java.util.Map<java.lang.String, java.lang.Object> e =
        finish(expected ? "REJECTED" : "FAILED", expected ? "REJECTED" : "FAILED");
    if (e == null) return;
    e.put("exceptionType", error.getClass().getName());
    if (details != null) {
      e.put("errorCategory", details.category());
      e.put("errorCode", details.code());
    }
    writer.emit(expected ? "INFO" : "WARN", e);
  }

  private String type(String suffix) {
    return (context.dependency() == null ? "PROCESS_" : "DEPENDENCY_") + suffix;
  }

  private Map<String, Object> finish(String suffix, String outcome) {
    if (terminal || closed) return null;
    terminal = true;
    java.util.Map<java.lang.String, java.lang.Object> e = writer.event(type(suffix), context);
    e.put("outcome", outcome);
    e.put("durationMs", Math.max(0, (System.nanoTime() - started) / 1_000_000));
    return e;
  }

  @Override
  public void close() {
    if (closed) return;
    try {
      if (!terminal) {
        span.error("INCOMPLETE");
        java.util.Map<java.lang.String, java.lang.Object> e = finish("INCOMPLETE", "FAILED");
        writer.emit("WARN", e);
      }
    } finally {
      closed = true;

      span.close();
    }
  }
}
