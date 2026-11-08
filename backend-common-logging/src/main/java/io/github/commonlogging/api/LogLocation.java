package io.github.commonlogging.api;

public final class LogLocation {
  private final String className;
  private final String methodName;

  @com.fasterxml.jackson.annotation.JsonCreator
  public LogLocation(
      @com.fasterxml.jackson.annotation.JsonProperty("className") String className,
      @com.fasterxml.jackson.annotation.JsonProperty("methodName") String methodName) {

    this.className = className;
    this.methodName = methodName;
  }

  public String className() {
    return className;
  }

  public String getClassName() {
    return className;
  }

  public String methodName() {
    return methodName;
  }

  public String getMethodName() {
    return methodName;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) return true;
    if (other == null || getClass() != other.getClass()) return false;
    LogLocation that = (LogLocation) other;
    return java.util.Objects.equals(className, that.className)
        && java.util.Objects.equals(methodName, that.methodName);
  }

  @Override
  public int hashCode() {
    return java.util.Objects.hash(className, methodName);
  }
}
