package io.github.commonlogging.internal;

import io.github.commonlogging.api.LogLocation;

public final class CallerLocationResolver {
  public LogLocation resolve() {
    for (StackTraceElement frame : Thread.currentThread().getStackTrace()) {
      String name = frame.getClassName();
      if (!name.startsWith("io.github.commonlogging.")
          && !name.startsWith("java.")
          && !name.startsWith("sun.")
          && !name.startsWith("org.springframework."))
        return new LogLocation(name, frame.getMethodName());
    }
    return null;
  }
}
