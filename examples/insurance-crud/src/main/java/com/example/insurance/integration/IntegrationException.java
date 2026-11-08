package com.example.insurance.integration;

import org.springframework.http.HttpStatus;

public final class IntegrationException extends RuntimeException {
  public enum Kind {
    TIMEOUT,
    UNAVAILABLE,
    INVALID_RESPONSE,
    REJECTED,
    FAILURE
  }

  private final String code;
  private final Kind kind;

  public IntegrationException(String code, Kind kind, Throwable cause) {
    super(code, cause);
    this.code = code;
    this.kind = kind;
  }

  public String code() {
    return code;
  }

  public Kind kind() {
    return kind;
  }

  public HttpStatus status() {
    return kind == Kind.TIMEOUT
        ? HttpStatus.GATEWAY_TIMEOUT
        : kind == Kind.REJECTED
            ? HttpStatus.UNPROCESSABLE_ENTITY
            : kind == Kind.INVALID_RESPONSE
                ? HttpStatus.BAD_GATEWAY
                : HttpStatus.SERVICE_UNAVAILABLE;
  }
}
