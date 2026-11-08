package io.github.commonlogging.spring;

import io.github.commonlogging.api.*;
import io.github.commonlogging.internal.*;
import io.github.commonlogging.payload.*;
import org.slf4j.*;
import org.springframework.boot.autoconfigure.*;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.*;
import org.springframework.core.Ordered;

@AutoConfiguration(
    afterName = {
      "org.springframework.boot.actuate.autoconfigure.opentelemetry.OpenTelemetryAutoConfiguration",
      "org.springframework.boot.actuate.autoconfigure.tracing.OpenTelemetryTracingAutoConfiguration"
    })
@EnableConfigurationProperties(LoggingProperties.class)
public class LoggingAutoConfiguration {
  @Bean(destroyMethod = "close")
  @ConditionalOnMissingBean(io.opentelemetry.api.OpenTelemetry.class)
  public io.opentelemetry.sdk.OpenTelemetrySdk commonOpenTelemetry() {
    return TracingRuntime.standaloneTelemetry();
  }

  @Bean
  @ConditionalOnMissingBean
  public TracingRuntime tracingRuntime(
      io.opentelemetry.api.OpenTelemetry telemetry,
      org.springframework.beans.factory.ObjectProvider<
              io.micrometer.observation.ObservationRegistry>
          observations) {
    return new TracingRuntime(telemetry, observations.getIfAvailable());
  }

  @Bean
  @ConditionalOnMissingBean
  public PayloadPolicies payloadPolicies() {
    return new PayloadPolicies();
  }

  @Bean
  @ConditionalOnMissingBean
  public LogOptions logOptions(LoggingProperties p) {
    p.validate();
    return new LogOptions(
        p.getApplication(),
        p.getEnvironment(),
        p.isEnabled(),
        p.isPayloadEnabled(),
        p.getMaxFrames(),
        p.getApplicationPackage());
  }

  @Bean
  @ConditionalOnMissingBean
  public PayloadLogSerializer payloadLogSerializer(PayloadPolicies policies, LoggingProperties p) {
    return new PayloadLogSerializer(
        policies, p.getMaxDepth(), p.getMaxItems(), p.getMaxString(), p.getMaxPayloadBytes());
  }

  @Bean
  @ConditionalOnMissingBean
  public EventSink eventSink(LoggingProperties properties) {
    Logger logger = LoggerFactory.getLogger("common.logging");
    if (!properties.isStructuredArgumentsEnabled())
      return (level, json) -> logger.atLevel(org.slf4j.event.Level.valueOf(level)).log(json);
    return new EventSink() {
      @Override
      public void write(String level, String json) {
        logger.atLevel(org.slf4j.event.Level.valueOf(level)).log(json);
      }

      @Override
      public void writeEvent(
          String level,
          java.util.Map<String, Object> event,
          com.fasterxml.jackson.databind.ObjectMapper mapper) {
        var builder = logger.atLevel(org.slf4j.event.Level.valueOf(level));
        // Convert the already sanitized tree into standard map/list/scalar values for the encoder.
        java.util.Map<String, Object> fields =
            mapper.convertValue(
                event,
                new com.fasterxml.jackson.core.type.TypeReference<
                    java.util.Map<String, Object>>() {});
        fields.remove("level"); // The standard encoder already writes severity.
        Object safeMessage = fields.remove("message");
        fields.forEach(builder::addKeyValue);
        var previous = MDC.getCopyOfContextMap();
        try {
          // These values already exist as event fields. Avoid duplicate JSON keys from MDC.
          fields.keySet().forEach(MDC::remove);
          java.util.List.of(
                  "message",
                  "level",
                  "level_value",
                  "@timestamp",
                  "@version",
                  "logger_name",
                  "thread_name")
              .forEach(MDC::remove);
          builder.log(safeMessage instanceof String text ? text : "common.logging.event");
        } finally {
          if (previous == null) MDC.clear();
          else MDC.setContextMap(previous);
        }
      }
    };
  }

  @Bean
  @ConditionalOnMissingBean
  public LoggingHealth loggingHealth() {
    return new LoggingHealth();
  }

  @Bean
  @ConditionalOnMissingBean
  public StructuredEventWriter structuredEventWriter(
      EventSink sink, LogOptions options, PayloadLogSerializer payloads, LoggingHealth health) {
    return new StructuredEventWriter(sink, options, payloads, health);
  }

  @Bean
  @ConditionalOnMissingBean
  public TLog tLog(StructuredEventWriter writer, TracingRuntime runtime) {
    return new TLog(writer, runtime);
  }

  @Bean
  @ConditionalOnMissingBean
  public ErrorClassifier errorClassifier() {
    return error -> {
      java.util.Set<Throwable> seen =
          java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
      for (Throwable t = error; t != null && seen.add(t); t = t.getCause()) {
        if (t instanceof java.net.SocketTimeoutException
            || t instanceof java.net.http.HttpTimeoutException
            || t instanceof java.util.concurrent.TimeoutException)
          return new ErrorDetails("DEPENDENCY_TIMEOUT", "DEPENDENCY_TIMEOUT", false);
        if (t instanceof java.net.ConnectException || t instanceof java.net.UnknownHostException)
          return new ErrorDetails("DEPENDENCY_UNAVAILABLE", "DEPENDENCY_UNAVAILABLE", false);
      }
      return new ErrorDetails("INTERNAL", "UNEXPECTED_ERROR", false);
    };
  }

  @Bean
  @ConditionalOnMissingBean
  public TExceptionLog tExceptionLog(
      StructuredEventWriter writer, ErrorClassifier classifier, TracingRuntime runtime) {
    return new TExceptionLog(writer, classifier, runtime);
  }

  @Bean
  @ConditionalOnMissingBean
  public ExceptionLoggingBridge exceptionLoggingBridge(TExceptionLog errors) {
    return new ExceptionLoggingBridge(errors);
  }

  @Bean
  @ConditionalOnMissingBean
  public DependencyLogging dependencyLogging(TLog log, ErrorClassifier classifier) {
    return new DependencyLogging(log, classifier);
  }

  @Configuration(proxyBeanMethods = false)
  @ConditionalOnClass(name = "ch.qos.logback.classic.LoggerContext")
  static class LogbackHealth {
    @Bean(destroyMethod = "close")
    AutoCloseable commonLoggingBackendMonitor(LoggingHealth health) {
      if (!(LoggerFactory.getILoggerFactory()
          instanceof ch.qos.logback.classic.LoggerContext context)) return () -> {};
      ch.qos.logback.core.status.StatusListener listener =
          status -> {
            if (status.getLevel() >= ch.qos.logback.core.status.Status.ERROR) health.backendError();
          };
      context.getStatusManager().add(listener);
      return () -> context.getStatusManager().remove(listener);
    }
  }

  @Configuration(proxyBeanMethods = false)
  @ConditionalOnClass(name = "org.aspectj.lang.ProceedingJoinPoint")
  @ConditionalOnProperty(
      prefix = "backend.common.logging",
      name = "operation-enabled",
      havingValue = "true",
      matchIfMissing = true)
  static class Operations {
    @Bean
    @ConditionalOnMissingBean
    LoggedOperationAspect loggedOperationAspect(TLog log, PayloadLogSerializer payloads) {
      return new LoggedOperationAspect(log, payloads);
    }
  }

  @Configuration(proxyBeanMethods = false)
  @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
  @ConditionalOnClass(
      name = "org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdvice")
  @ConditionalOnProperty(
      prefix = "backend.common.logging",
      name = "web-enabled",
      havingValue = "true",
      matchIfMissing = true)
  static class Web {
    @Bean
    FilterRegistrationBean<TraceFilter> commonTraceFilter(
        StructuredEventWriter writer, LoggingProperties p, TracingRuntime runtime) {
      var bean = new FilterRegistrationBean<>(new TraceFilter(writer, p, runtime));
      bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
      bean.setAsyncSupported(true);
      bean.setDispatcherTypes(
          jakarta.servlet.DispatcherType.REQUEST,
          jakarta.servlet.DispatcherType.ASYNC,
          jakarta.servlet.DispatcherType.ERROR);
      return bean;
    }

    @Bean
    PayloadLoggingAdvice payloadLoggingAdvice(TLog log, LoggingProperties p) {
      return new PayloadLoggingAdvice(log, p);
    }
  }
}
