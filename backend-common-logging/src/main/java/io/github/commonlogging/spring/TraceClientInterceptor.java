package io.github.commonlogging.spring;

import io.github.commonlogging.internal.LogContext;
import java.io.IOException;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.*;

/** Inject current span as W3C remote parent on explicitly enabled destinations. */
public final class TraceClientInterceptor implements ClientHttpRequestInterceptor {
  private final LoggingProperties properties;

  public TraceClientInterceptor(LoggingProperties properties) {
    this.properties = properties;
  }

  @Override
  public ClientHttpResponse intercept(
      HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
    String host = request.getURI().getHost(), header = LogContext.traceparent();
    if (header != null
        && host != null
        && properties.getPropagationHosts().stream().anyMatch(host::equalsIgnoreCase))
      request.getHeaders().set("traceparent", header);
    return execution.execute(request, body);
  }
}
