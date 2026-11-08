package io.github.commonlogging.internal;

import io.github.commonlogging.api.LogLocation;

public final class CallerLocationResolver {
  public LogLocation resolve() {
    return StackWalker.getInstance()
        .walk(
            frames ->
                frames
                    .filter(
                        f ->
                            !f.getClassName().startsWith("io.github.commonlogging.")
                                && !f.getClassName().startsWith("java.")
                                && !f.getClassName().startsWith("org.springframework."))
                    .findFirst()
                    .map(f -> new LogLocation(f.getClassName(), f.getMethodName()))
                    .orElse(null));
  }
}
