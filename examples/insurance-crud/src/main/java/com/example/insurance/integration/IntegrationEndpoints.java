package com.example.insurance.integration;

import java.net.URI;

public final class IntegrationEndpoints {
  private final URI core;
  private final URI notification;
  private final URI s3;

  @com.fasterxml.jackson.annotation.JsonCreator
  public IntegrationEndpoints(
      @com.fasterxml.jackson.annotation.JsonProperty("core") URI core,
      @com.fasterxml.jackson.annotation.JsonProperty("notification") URI notification,
      @com.fasterxml.jackson.annotation.JsonProperty("s3") URI s3) {

    this.core = core;
    this.notification = notification;
    this.s3 = s3;
  }

  public URI core() {
    return core;
  }

  public URI getCore() {
    return core;
  }

  public URI notification() {
    return notification;
  }

  public URI getNotification() {
    return notification;
  }

  public URI s3() {
    return s3;
  }

  public URI getS3() {
    return s3;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) return true;
    if (other == null || getClass() != other.getClass()) return false;
    IntegrationEndpoints that = (IntegrationEndpoints) other;
    return java.util.Objects.equals(core, that.core)
        && java.util.Objects.equals(notification, that.notification)
        && java.util.Objects.equals(s3, that.s3);
  }

  @Override
  public int hashCode() {
    return java.util.Objects.hash(core, notification, s3);
  }
}
