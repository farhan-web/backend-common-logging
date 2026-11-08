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

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(LoggingProperties.class)
public class LoggingAutoConfiguration {
  @Bean(destroyMethod = "close")
  @ConditionalOnMissingBean(io.opentelemetry.api.OpenTelemetry.class)
  public io.opentelemetry.sdk.OpenTelemetrySdk commonOpenTelemetry(
      org.springframework.beans.factory.ObjectProvider<io.opentelemetry.sdk.trace.SpanProcessor>
          processors,
      LoggingProperties properties) {
    io.opentelemetry.sdk.trace.SdkTracerProviderBuilder provider =
        io.opentelemetry.sdk.trace.SdkTracerProvider.builder()
            .setSampler(
                io.opentelemetry.sdk.trace.samplers.Sampler.parentBased(
                    io.opentelemetry.sdk.trace.samplers.Sampler.alwaysOn()))
            .setResource(
                io.opentelemetry.sdk.resources.Resource.getDefault()
                    .merge(
                        io.opentelemetry.sdk.resources.Resource.create(
                            io.opentelemetry.api.common.Attributes.of(
                                io.opentelemetry.api.common.AttributeKey.stringKey("service.name"),
                                properties.getApplication()))));
    processors.orderedStream().forEach(provider::addSpanProcessor);
    return io.opentelemetry.sdk.OpenTelemetrySdk.builder()
        .setTracerProvider(provider.build())
        .setPropagators(
            io.opentelemetry.context.propagation.ContextPropagators.create(
                io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator.getInstance()))
        .build();
  }

  @Bean
  @ConditionalOnMissingBean
  public TracingRuntime tracingRuntime(io.opentelemetry.api.OpenTelemetry telemetry) {
    return new TracingRuntime(telemetry);
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
  public EventSink eventSink() {
    final Logger logger = LoggerFactory.getLogger("common.logging");
    return (level, json) -> {
      if ("ERROR".equals(level)) logger.error(json);
      else if ("WARN".equals(level)) logger.warn(json);
      else logger.info(json);
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
  @ConditionalOnClass(name = "org.springframework.web.client.RestTemplate")
  static class Clients {
    @Bean
    @ConditionalOnMissingBean
    TraceClientInterceptor traceClientInterceptor(
        LoggingProperties properties, TracingRuntime runtime) {
      return new TraceClientInterceptor(properties, runtime);
    }

    @Bean
    org.springframework.boot.web.client.RestTemplateCustomizer commonTraceRestTemplateCustomizer(
        TraceClientInterceptor interceptor) {
      return template -> {
        if (!template.getInterceptors().contains(interceptor))
          template.getInterceptors().add(interceptor);
      };
    }
  }

  @Configuration(proxyBeanMethods = false)
  @ConditionalOnClass(name = "ch.qos.logback.classic.LoggerContext")
  static class LogbackHealth {
    @Bean(destroyMethod = "close")
    AutoCloseable commonLoggingBackendMonitor(LoggingHealth health) {
      if (!(LoggerFactory.getILoggerFactory() instanceof ch.qos.logback.classic.LoggerContext))
        return () -> {};
      ch.qos.logback.classic.LoggerContext context =
          (ch.qos.logback.classic.LoggerContext) LoggerFactory.getILoggerFactory();
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
      org.springframework.boot.web.servlet.FilterRegistrationBean<
              io.github.commonlogging.spring.TraceFilter>
          bean = new FilterRegistrationBean<>(new TraceFilter(writer, p, runtime));
      bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
      bean.setAsyncSupported(true);
      bean.setDispatcherTypes(
          javax.servlet.DispatcherType.REQUEST,
          javax.servlet.DispatcherType.ASYNC,
          javax.servlet.DispatcherType.ERROR);
      return bean;
    }

    @Bean
    PayloadLoggingAdvice payloadLoggingAdvice(TLog log, LoggingProperties p) {
      return new PayloadLoggingAdvice(log, p);
    }
  }
}
