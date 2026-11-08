package io.github.commonlogging.spring;

import io.github.commonlogging.internal.*;
import java.io.IOException;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.*;

/** Java 8 / Boot 2 transport span and W3C propagation on explicitly allowed destinations. */
public final class TraceClientInterceptor implements ClientHttpRequestInterceptor {
  private final LoggingProperties properties;
  private final TracingRuntime runtime;

  public TraceClientInterceptor(LoggingProperties properties) {
    this(properties, TracingRuntime.standalone());
  }

  public TraceClientInterceptor(LoggingProperties properties, TracingRuntime runtime) {
    this.properties = properties;
    this.runtime = runtime;
  }

  @Override
  public ClientHttpResponse intercept(
      HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
    String host = request.getURI().getHost();
    if (host == null || properties.getPropagationHosts().stream().noneMatch(host::equalsIgnoreCase))
      return execution.execute(request, body);
    if (LogContext.operation() != null && "CLIENT".equals(LogContext.operation().spanKind())) {
      request.getHeaders().set("traceparent", LogContext.traceparent());
      return execution.execute(request, body);
    }
    try (LogContext.SpanScope span =
        LogContext.start(LogContext.runtime(runtime), "http.client", "CLIENT", null, null)) {
      request.getHeaders().set("traceparent", LogContext.traceparent());
      span.span().setAttribute("http.method", request.getMethodValue());
      try {
        ClientHttpResponse response = execution.execute(request, body);
        int status = response.getRawStatusCode();
        span.span().setAttribute("http.status_code", status);
        if (status >= 500) span.error("HTTP_5XX");
        return response;
      } catch (IOException | RuntimeException failure) {
        span.error(failure.getClass().getName());
        throw failure;
      }
    }
  }
}
