package io.github.commonlogging.spring;

import io.github.commonlogging.api.*;
import io.github.commonlogging.internal.CallerLocationResolver;
import java.util.function.Supplier;

/** One logical call; never retries or changes the business exception. */
public final class DependencyLogging {
  private final TLog log;
  private final ErrorClassifier classifier;

  public DependencyLogging(TLog log) {
    this(log, e -> new ErrorDetails("DEPENDENCY_FAILURE", "DEPENDENCY_FAILURE", false));
  }

  public DependencyLogging(TLog log, ErrorClassifier classifier) {
    this.log = log;
    this.classifier = classifier;
  }

  public <T> T call(String stableOperation, Object requestDto, Supplier<T> action) {
    return run(
        log.startAt(stableOperation, requestDto, true, new CallerLocationResolver().resolve()),
        action,
        false);
  }

  public <T> T call(String dependency, String operation, Object requestDto, Supplier<T> action) {
    return run(
        log.startDependency(
            dependency, operation, requestDto, new CallerLocationResolver().resolve()),
        action,
        true);
  }

  public <T> T callHttp(
      String dependency, String operation, Object requestDto, Supplier<T> action) {
    return run(
        log.startHttpDependency(
            dependency, operation, requestDto, new CallerLocationResolver().resolve()),
        action,
        true);
  }

  private <T> T run(LogOperation operation, Supplier<T> action, boolean classify) {
    try (operation) {
      try {
        T result = action.get();
        operation.end(result);
        return result;
      } catch (RuntimeException ex) {
        ErrorDetails details = null;
        if (classify)
          try {
            details = classifier.classify(ex);
          } catch (RuntimeException ignored) {
          }
        operation.fail(ex, details);
        throw ex;
      }
    }
  }
}
