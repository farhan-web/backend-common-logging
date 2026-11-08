package io.github.commonlogging.api;

/** Immutable runtime options. Payloads require this switch AND DTO opt-in. */
public record LogOptions(
    String application,
    String environment,
    boolean enabled,
    boolean payloadEnabled,
    int maxFrames,
    String applicationPackage) {
  public LogOptions {
    if (application == null || environment == null || maxFrames < 1 || maxFrames > 256)
      throw new IllegalArgumentException("Invalid logging options");
    if (applicationPackage == null) applicationPackage = "";
  }
}
