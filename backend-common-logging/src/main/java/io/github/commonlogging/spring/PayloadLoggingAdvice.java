package io.github.commonlogging.spring;

import io.github.commonlogging.api.*;
import java.lang.reflect.Type;
import org.springframework.core.MethodParameter;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.*;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.context.request.*;
import org.springframework.web.servlet.mvc.method.annotation.*;

@ControllerAdvice
public final class PayloadLoggingAdvice extends RequestBodyAdviceAdapter
    implements ResponseBodyAdvice<Object> {
  private static final String CAPTURED = PayloadLoggingAdvice.class.getName() + ".requestCaptured";
  private final TLog log;
  private final LoggingProperties properties;

  public PayloadLoggingAdvice(TLog log, LoggingProperties properties) {
    this.log = log;
    this.properties = properties;
  }

  @Override
  public boolean supports(MethodParameter p, Type t, Class<? extends HttpMessageConverter<?>> c) {
    return enabled();
  }

  @Override
  public Object afterBodyRead(
      Object body,
      HttpInputMessage message,
      MethodParameter parameter,
      Type type,
      Class<? extends HttpMessageConverter<?>> converter) {
    org.springframework.web.context.request.ServletRequestAttributes attributes =
        (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
    if (attributes != null && attributes.getRequest().getAttribute(CAPTURED) == null) {
      log.payload("REQUEST_PAYLOAD", body, location(parameter));
      attributes.getRequest().setAttribute(CAPTURED, true);
    }
    return body;
  }

  @Override
  public boolean supports(MethodParameter p, Class<? extends HttpMessageConverter<?>> c) {
    return enabled();
  }

  @Override
  public Object beforeBodyWrite(
      Object body,
      MethodParameter parameter,
      MediaType contentType,
      Class<? extends HttpMessageConverter<?>> converter,
      ServerHttpRequest request,
      ServerHttpResponse response) {
    log.payload("RESPONSE_PAYLOAD", body, location(parameter));
    return body;
  }

  private boolean enabled() {
    if (!properties.isEnabled() || !properties.isPayloadEnabled()) return false;
    org.springframework.web.context.request.RequestAttributes attributes =
        RequestContextHolder.getRequestAttributes();
    if (!(attributes instanceof ServletRequestAttributes)) return false;
    ServletRequestAttributes servlet = (ServletRequestAttributes) attributes;
    String path = servlet.getRequest().getRequestURI();
    return properties.getPayloadPaths().stream()
        .anyMatch(pattern -> new AntPathMatcher().match(pattern, path));
  }

  private LogLocation location(MethodParameter p) {
    return new LogLocation(
        p.getContainingClass().getName(),
        p.getMethod() == null ? "unknown" : p.getMethod().getName());
  }
}
