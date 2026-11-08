package io.github.commonlogging.spring;

import io.github.commonlogging.api.TExceptionLog;

/**
 * Call from the application's existing advice/security/job error boundary. No response mapping is
 * installed.
 */
public final class ExceptionLoggingBridge {
  private final TExceptionLog errors;

  public ExceptionLoggingBridge(TExceptionLog errors) {
    this.errors = errors;
  }

  public void log(Throwable cause) {
    errors.error("request", cause);
  }
}
