package com.example.insurance.integration;

import com.example.insurance.demo.LocalIntegrationServer;
import io.github.commonlogging.api.*;
import java.net.URI;
import java.time.Duration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import software.amazon.awssdk.auth.credentials.*;
import software.amazon.awssdk.core.checksums.*;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.retries.StandardRetryStrategy;
import software.amazon.awssdk.services.s3.*;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(IntegrationProperties.class)
public class IntegrationConfiguration {
  @Bean(destroyMethod = "close")
  @ConditionalOnProperty(name = "insurance.demo.enabled", havingValue = "true")
  LocalIntegrationServer localIntegrationServer(
      TLog log, TExceptionLog errors, @Value("${insurance.demo.port:9090}") int port) {
    return new LocalIntegrationServer(port, log, errors);
  }

  @Bean
  IntegrationEndpoints integrationEndpoints(
      IntegrationProperties p, ObjectProvider<LocalIntegrationServer> demo) {
    LocalIntegrationServer simulator = demo.getIfAvailable();
    if (simulator != null) {
      String root = "http://127.0.0.1:" + simulator.port();
      return new IntegrationEndpoints(
          URI.create(root + "/core"), URI.create(root), URI.create(root + "/s3"));
    }
    return new IntegrationEndpoints(p.coreUrl(), p.notificationUrl(), p.s3().endpoint());
  }

  @Bean(destroyMethod = "close")
  S3Client s3Client(IntegrationProperties p, IntegrationEndpoints endpoints) {
    boolean local = "local".equals(p.s3().mode());
    if (!local && !"aws".equals(p.s3().mode()))
      throw new IllegalArgumentException("S3 mode must be local or aws");
    var builder =
        S3Client.builder()
            .region(Region.of(p.s3().region()))
            .credentialsProvider(
                local
                    ? StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("local-demo", "local-demo"))
                    : DefaultCredentialsProvider.create())
            .httpClientBuilder(
                UrlConnectionHttpClient.builder()
                    .connectionTimeout(p.connectTimeout())
                    .socketTimeout(p.readTimeout()))
            .overrideConfiguration(
                config ->
                    config
                        .apiCallTimeout(p.readTimeout().plus(Duration.ofSeconds(1)))
                        .apiCallAttemptTimeout(p.readTimeout())
                        .retryStrategy(StandardRetryStrategy.builder().maxAttempts(1).build()))
            .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
            .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
            .serviceConfiguration(
                S3Configuration.builder()
                    .pathStyleAccessEnabled(local)
                    .chunkedEncodingEnabled(false)
                    .build());
    if (local && endpoints.s3() == null)
      throw new IllegalArgumentException(
          "Local S3 requires a configured endpoint or demo simulator");
    if (endpoints.s3() != null) builder.endpointOverride(endpoints.s3());
    return builder.build();
  }
}
