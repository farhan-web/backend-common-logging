package io.github.commonlogging.spring;

import io.github.commonlogging.api.*;
import io.github.commonlogging.internal.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.servlet.*;
import javax.servlet.http.*;
import org.springframework.web.servlet.HandlerMapping;

/** One root SERVER span per logical servlet request, reused across async/error dispatches. */
public final class TraceFilter implements Filter {
  private static final String STATE = TraceFilter.class.getName() + ".state";
  private final StructuredEventWriter writer;
  private final LoggingProperties properties;
  private final TracingRuntime runtime;

  public TraceFilter(StructuredEventWriter writer, LoggingProperties properties) {
    this(writer, properties, TracingRuntime.standalone());
  }

  public TraceFilter(
      StructuredEventWriter writer, LoggingProperties properties, TracingRuntime runtime) {
    this.writer = writer;
    this.runtime = runtime;
    this.properties = properties;
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    javax.servlet.http.HttpServletRequest req = (HttpServletRequest) request;
    javax.servlet.http.HttpServletResponse res = (HttpServletResponse) response;
    RequestState state = (RequestState) req.getAttribute(STATE);
    boolean initial = state == null;
    if (initial) {
      state = new RequestState(runtime, extract(req));
      req.setAttribute(STATE, state);
    }
    RequestState current = state;
    try (io.github.commonlogging.internal.LogContext.Scope ignored =
        LogContext.attach(runtime, state.span, state.root)) {
      if (!res.isCommitted()) {
        res.setHeader(properties.getTraceIdHeader(), state.root.traceId());
        res.setHeader("X-Span-ID", state.root.spanId());
      }
      if (initial) {
        java.util.Map<java.lang.String, java.lang.Object> event =
            writer.event("REQUEST_START", state.root);
        event.put("httpMethod", req.getMethod());
        event.put("spanKind", "SERVER");
        writer.emit("INFO", event);
      }
      try {
        chain.doFilter(request, response);
      } catch (IOException | ServletException | RuntimeException ex) {
        state.failed = true;
        throw ex;
      } finally {
        if (req.isAsyncStarted()) {
          if (state.listenerInstalled.compareAndSet(false, true))
            try {
              req.getAsyncContext()
                  .addListener(
                      new AsyncListener() {
                        @Override
                        public void onComplete(AsyncEvent e) {
                          complete(current, req, res);
                        }

                        @Override
                        public void onTimeout(AsyncEvent e) {
                          current.failed = true;
                        }

                        @Override
                        public void onError(AsyncEvent e) {
                          current.failed = true;
                        }

                        @Override
                        public void onStartAsync(AsyncEvent e) {
                          e.getAsyncContext().addListener(this);
                        }
                      });
            } catch (IllegalStateException race) {
              complete(state, req, res);
            }
        } else complete(state, req, res);
      }
    }
  }

  private void complete(
      RequestState state, HttpServletRequest request, HttpServletResponse response) {
    if (!state.done.compareAndSet(false, true)) return;
    try (io.github.commonlogging.internal.LogContext.Scope ignored =
        LogContext.attach(runtime, state.span, state.root)) {
      java.util.Map<java.lang.String, java.lang.Object> e = writer.event("REQUEST_END", state.root);
      int status = response.getStatus();
      if (!state.failed || status >= 400 || response.isCommitted()) e.put("httpStatus", status);
      else e.put("responseStatusKnown", false);
      e.put(
          "outcome",
          state.failed || status >= 500 ? "FAILED" : status >= 400 ? "REJECTED" : "SUCCESS");
      e.put("spanKind", "SERVER");
      e.put("durationMs", Math.max(0, (System.nanoTime() - state.started) / 1000000));
      Object route = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
      if (route != null) e.put("route", route.toString());
      writer.emit("INFO", e);
      if (state.failed || status >= 500)
        state.span.setStatus(io.opentelemetry.api.trace.StatusCode.ERROR);
    } finally {
      if (state.owned) state.span.end();
    }
  }

  private String extract(HttpServletRequest request) {
    java.util.ArrayList<java.lang.String> headers =
        Collections.list(request.getHeaders("traceparent"));
    return headers.size() == 1 ? headers.get(0) : null;
  }

  private static final class RequestState {
    final FailureContext root;
    final io.opentelemetry.api.trace.Span span;
    final boolean owned;
    final long started = System.nanoTime();
    final AtomicBoolean done = new AtomicBoolean(), listenerInstalled = new AtomicBoolean();
    volatile boolean failed;

    RequestState(TracingRuntime runtime, String upstream) {
      io.opentelemetry.api.trace.Span active = io.opentelemetry.api.trace.Span.current();
      owned = !active.getSpanContext().isValid();
      span =
          owned
              ? runtime
                  .tracer()
                  .spanBuilder("http.request")
                  .setParent(runtime.extract(upstream))
                  .setSpanKind(io.opentelemetry.api.trace.SpanKind.SERVER)
                  .startSpan()
              : active;
      root = TracingRuntime.context(span, "http.request", null, null, "SERVER", null);
    }
  }
}
