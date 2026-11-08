package io.github.commonlogging.internal;

import io.github.commonlogging.api.*;
import io.opentelemetry.api.trace.*;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.trace.IdGenerator;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.MDC;

/** Only business metadata/failure ownership is local. IDs, spans and propagation belong to OTel. */
public final class LogContext {
  private static final ThreadLocal<State> LOCAL = new ThreadLocal<>();

  private LogContext() {}

  public static Scope open(String candidate) {
    return open(candidate, "00", null, false, TracingRuntime.standalone());
  }

  public static Scope open(String candidate, String flags, String remote) {
    return open(candidate, flags, remote, false, runtime(TracingRuntime.standalone()));
  }

  public static Scope open(String candidate, String flags, String remote, boolean server) {
    return open(candidate, flags, remote, server, runtime(TracingRuntime.standalone()));
  }

  public static Scope open(
      String candidate, String flags, String remote, boolean server, TracingRuntime runtime) {
    Context context = Context.root();
    if (TraceId.isValid(candidate)) {
      String parent = remote == null ? IdGenerator.random().generateSpanId() : remote;
      SpanContext seed =
          SpanContext.createFromRemoteParent(
              candidate, parent, TraceFlags.fromHex(flags, 0), TraceState.getDefault());
      context = context.with(Span.wrap(seed));
    }
    return attachContext(runtime, context, null, server);
  }

  public static Scope incoming(TracingRuntime runtime, String traceparent) {
    return attachContext(runtime, runtime.extract(traceparent), null, true);
  }

  public static TracingRuntime runtime(TracingRuntime fallback) {
    State s = LOCAL.get();
    return s == null ? fallback : s.runtime;
  }

  public static SpanScope start(
      TracingRuntime runtime, String name, String kind, LogLocation location, String dependency) {
    String parent = parentSpanId();
    Span span = runtime.tracer().spanBuilder(name).setSpanKind(SpanKind.valueOf(kind)).startSpan();
    FailureContext metadata =
        TracingRuntime.context(span, name, location, dependency, kind, parent);
    return new SpanScope(
        span,
        metadata,
        attachContext(runtime, Context.current().with(span), metadata, false),
        true,
        () -> {});
  }

  public static Scope attach(TracingRuntime runtime, Span span, FailureContext metadata) {
    return attachContext(runtime, Context.current().with(span), metadata, false);
  }

  private static Scope attachContext(
      TracingRuntime runtime, Context context, FailureContext operation, boolean server) {
    State previous = LOCAL.get();
    Map<String, String> old = new HashMap<>();
    for (String key :
        io.github.commonlogging.internal.Java8Collections.list("traceId", "spanId", "parentSpanId"))
      old.put(key, MDC.get(key));
    io.opentelemetry.context.Scope standard = context.makeCurrent();
    IdentityHashMap<Throwable, FailureContext> failures =
        previous != null && Objects.equals(previous.trace, traceId())
            ? previous.failures
            : new IdentityHashMap<>();
    LOCAL.set(new State(runtime, operation, server, traceId(), failures));
    sync();
    AtomicBoolean closed = new AtomicBoolean();
    return () -> {
      if (!closed.compareAndSet(false, true)) return;
      standard.close();
      if (previous == null) LOCAL.remove();
      else LOCAL.set(previous);
      old.forEach(
          (k, v) -> {
            if (v == null) MDC.remove(k);
            else MDC.put(k, v);
          });
    };
  }

  public static String traceId() {
    SpanContext c = Span.current().getSpanContext();
    return c.isValid() ? c.getTraceId() : null;
  }

  public static String traceFlags() {
    return Span.current().getSpanContext().getTraceFlags().asHex();
  }

  public static String parentSpanId() {
    SpanContext c = Span.current().getSpanContext();
    return c.isValid() ? c.getSpanId() : null;
  }

  public static boolean serverEntry() {
    State s = LOCAL.get();
    return s != null && s.server && s.operation == null;
  }

  public static String traceparent() {
    return runtime(TracingRuntime.standalone()).traceparent();
  }

  public static FailureContext operation() {
    State s = LOCAL.get();
    return s == null ? null : s.operation;
  }

  public static void operation(FailureContext value) {
    State s = LOCAL.get();
    if (s != null) {
      s.operation = value;
      sync();
    }
  }

  private static void sync() {
    put("traceId", traceId());
    FailureContext c = operation();
    put("spanId", c == null ? null : c.spanId());
    put("parentSpanId", c == null ? null : c.parentSpanId());
  }

  private static void put(String key, String value) {
    if (value == null) MDC.remove(key);
    else MDC.put(key, value);
  }

  public static void failed(Throwable error, FailureContext context) {
    State s = LOCAL.get();
    if (s != null && s.failures.size() < 16) s.failures.putIfAbsent(error, context);
  }

  public static FailureContext failure(Throwable error) {
    State s = LOCAL.get();
    if (s == null) return null;
    Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
    for (Throwable e = error; e != null && seen.add(e); e = e.getCause())
      if (s.failures.containsKey(e)) return s.failures.get(e);
    return s.operation;
  }

  public static void recovered(Throwable error) {
    State s = LOCAL.get();
    if (s == null) return;
    Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
    for (Throwable e = error; e != null && seen.add(e); e = e.getCause()) s.failures.remove(e);
  }

  public static Runnable wrap(Runnable task) {
    Context standard = Context.current();
    State s = LOCAL.get();
    TracingRuntime runtime = runtime(TracingRuntime.standalone());
    FailureContext operation = operation();
    return () -> {
      try (io.github.commonlogging.internal.LogContext.Scope ignored =
          attachContext(runtime, standard, operation, s != null && s.server)) {
        task.run();
      }
    };
  }

  public interface Scope extends AutoCloseable {
    @Override
    void close();
  }

  public static final class SpanScope implements AutoCloseable {
    private final Span span;
    private final FailureContext context;
    private final Scope scope;
    private final boolean owned;
    private final Runnable finish;
    private boolean closed;

    SpanScope(Span span, FailureContext context, Scope scope, boolean owned, Runnable finish) {
      this.span = span;
      this.context = context;
      this.scope = scope;
      this.owned = owned;
      this.finish = finish;
    }

    public Span span() {
      return span;
    }

    public FailureContext context() {
      return context;
    }

    public void error(String type) {
      span.setStatus(StatusCode.ERROR);
      span.setAttribute("error.type", type);
    }

    @Override
    public void close() {
      if (closed) return;
      closed = true;
      try {
        scope.close();
      } finally {
        if (owned) span.end();
        finish.run();
      }
    }
  }

  private static final class State {
    final TracingRuntime runtime;
    final boolean server;
    final String trace;
    final IdentityHashMap<Throwable, FailureContext> failures;
    FailureContext operation;

    State(
        TracingRuntime runtime,
        FailureContext operation,
        boolean server,
        String trace,
        IdentityHashMap<Throwable, FailureContext> failures) {
      this.runtime = runtime;
      this.operation = operation;
      this.server = server;
      this.trace = trace;
      this.failures = failures;
    }
  }
}
