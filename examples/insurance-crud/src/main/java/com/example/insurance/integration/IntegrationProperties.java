package com.example.insurance.integration;

import java.net.URI;
import java.time.Duration;
import javax.validation.Valid;
import javax.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties("insurance.integrations")
@Validated
@org.springframework.boot.context.properties.ConstructorBinding
public final class IntegrationProperties {
  private final @NotNull URI coreUrl;
  private final @NotNull URI notificationUrl;
  private final @NotNull Duration connectTimeout;
  private final @NotNull Duration readTimeout;
  private final @Valid @NotNull S3 s3;

  @com.fasterxml.jackson.annotation.JsonCreator
  public IntegrationProperties(
      @com.fasterxml.jackson.annotation.JsonProperty("coreUrl") URI coreUrl,
      @com.fasterxml.jackson.annotation.JsonProperty("notificationUrl") URI notificationUrl,
      @com.fasterxml.jackson.annotation.JsonProperty("connectTimeout") Duration connectTimeout,
      @com.fasterxml.jackson.annotation.JsonProperty("readTimeout") Duration readTimeout,
      @com.fasterxml.jackson.annotation.JsonProperty("s3") S3 s3) {

    this.coreUrl = coreUrl;
    this.notificationUrl = notificationUrl;
    this.connectTimeout = connectTimeout;
    this.readTimeout = readTimeout;
    this.s3 = s3;
  }

  public URI coreUrl() {
    return coreUrl;
  }

  public URI getCoreUrl() {
    return coreUrl;
  }

  public URI notificationUrl() {
    return notificationUrl;
  }

  public URI getNotificationUrl() {
    return notificationUrl;
  }

  public Duration connectTimeout() {
    return connectTimeout;
  }

  public Duration getConnectTimeout() {
    return connectTimeout;
  }

  public Duration readTimeout() {
    return readTimeout;
  }

  public Duration getReadTimeout() {
    return readTimeout;
  }

  public S3 s3() {
    return s3;
  }

  public S3 getS3() {
    return s3;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) return true;
    if (other == null || getClass() != other.getClass()) return false;
    IntegrationProperties that = (IntegrationProperties) other;
    return java.util.Objects.equals(coreUrl, that.coreUrl)
        && java.util.Objects.equals(notificationUrl, that.notificationUrl)
        && java.util.Objects.equals(connectTimeout, that.connectTimeout)
        && java.util.Objects.equals(readTimeout, that.readTimeout)
        && java.util.Objects.equals(s3, that.s3);
  }

  @Override
  public int hashCode() {
    return java.util.Objects.hash(coreUrl, notificationUrl, connectTimeout, readTimeout, s3);
  }

  public static final class S3 {
    private final @NotBlank String mode;
    private final URI endpoint;
    private final @NotBlank String region;
    private final @NotBlank String bucket;

    @com.fasterxml.jackson.annotation.JsonCreator
    public S3(
        @com.fasterxml.jackson.annotation.JsonProperty("mode") String mode,
        @com.fasterxml.jackson.annotation.JsonProperty("endpoint") URI endpoint,
        @com.fasterxml.jackson.annotation.JsonProperty("region") String region,
        @com.fasterxml.jackson.annotation.JsonProperty("bucket") String bucket) {

      this.mode = mode;
      this.endpoint = endpoint;
      this.region = region;
      this.bucket = bucket;
    }

    public String mode() {
      return mode;
    }

    public String getMode() {
      return mode;
    }

    public URI endpoint() {
      return endpoint;
    }

    public URI getEndpoint() {
      return endpoint;
    }

    public String region() {
      return region;
    }

    public String getRegion() {
      return region;
    }

    public String bucket() {
      return bucket;
    }

    public String getBucket() {
      return bucket;
    }

    @Override
    public boolean equals(Object other) {
      if (this == other) return true;
      if (other == null || getClass() != other.getClass()) return false;
      S3 that = (S3) other;
      return java.util.Objects.equals(mode, that.mode)
          && java.util.Objects.equals(endpoint, that.endpoint)
          && java.util.Objects.equals(region, that.region)
          && java.util.Objects.equals(bucket, that.bucket);
    }

    @Override
    public int hashCode() {
      return java.util.Objects.hash(mode, endpoint, region, bucket);
    }
  }
}
