package com.example.insurance.error;

import org.springframework.http.HttpStatus;

/** Application-owned error contract; the logging library knows no insurance rules. */
public final class BusinessException extends RuntimeException {
  private final String code;
  private final HttpStatus status;

  public BusinessException(String code, String safeMessage, HttpStatus status) {
    super(safeMessage);
    this.code = code;
    this.status = status;
  }

  public String code() {
    return code;
  }

  public HttpStatus status() {
    return status;
  }
}
