package com.example.insurance.integration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties("insurance.integrations")
@Validated
public record IntegrationProperties(
    @NotNull URI coreUrl,
    @NotNull URI notificationUrl,
    @NotNull Duration connectTimeout,
    @NotNull Duration readTimeout,
    @Valid @NotNull S3 s3) {
  public record S3(
      @NotBlank String mode, URI endpoint, @NotBlank String region, @NotBlank String bucket) {}
}
