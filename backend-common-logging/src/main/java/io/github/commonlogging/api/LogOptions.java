package io.github.commonlogging.api;

/** Immutable runtime options. Payloads require this switch AND DTO opt-in. */
public final class LogOptions {
  private final String application;
  private final String environment;
  private final boolean enabled;
  private final boolean payloadEnabled;
  private final int maxFrames;
  private final String applicationPackage;

  @com.fasterxml.jackson.annotation.JsonCreator
  public LogOptions(
      @com.fasterxml.jackson.annotation.JsonProperty("application") String application,
      @com.fasterxml.jackson.annotation.JsonProperty("environment") String environment,
      @com.fasterxml.jackson.annotation.JsonProperty("enabled") boolean enabled,
      @com.fasterxml.jackson.annotation.JsonProperty("payloadEnabled") boolean payloadEnabled,
      @com.fasterxml.jackson.annotation.JsonProperty("maxFrames") int maxFrames,
      @com.fasterxml.jackson.annotation.JsonProperty("applicationPackage")
          String applicationPackage) {

    if (application == null || environment == null || maxFrames < 1 || maxFrames > 256)
      throw new IllegalArgumentException("Invalid logging options");
    if (applicationPackage == null) applicationPackage = "";

    this.application = application;
    this.environment = environment;
    this.enabled = enabled;
    this.payloadEnabled = payloadEnabled;
    this.maxFrames = maxFrames;
    this.applicationPackage = applicationPackage;
  }

  public String application() {
    return application;
  }

  public String getApplication() {
    return application;
  }

  public String environment() {
    return environment;
  }

  public String getEnvironment() {
    return environment;
  }

  public boolean enabled() {
    return enabled;
  }

  public boolean getEnabled() {
    return enabled;
  }

  public boolean payloadEnabled() {
    return payloadEnabled;
  }

  public boolean getPayloadEnabled() {
    return payloadEnabled;
  }

  public int maxFrames() {
    return maxFrames;
  }

  public int getMaxFrames() {
    return maxFrames;
  }

  public String applicationPackage() {
    return applicationPackage;
  }

  public String getApplicationPackage() {
    return applicationPackage;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) return true;
    if (other == null || getClass() != other.getClass()) return false;
    LogOptions that = (LogOptions) other;
    return java.util.Objects.equals(application, that.application)
        && java.util.Objects.equals(environment, that.environment)
        && java.util.Objects.equals(enabled, that.enabled)
        && java.util.Objects.equals(payloadEnabled, that.payloadEnabled)
        && java.util.Objects.equals(maxFrames, that.maxFrames)
        && java.util.Objects.equals(applicationPackage, that.applicationPackage);
  }

  @Override
  public int hashCode() {
    return java.util.Objects.hash(
        application, environment, enabled, payloadEnabled, maxFrames, applicationPackage);
  }
}
