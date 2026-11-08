package com.example.insurance.error;

import io.github.commonlogging.annotation.LogPayload;
import io.github.commonlogging.annotation.LogValue;
import java.util.List;

@LogPayload
public final class ApiError {
  private final @LogValue String code;
  private final @LogValue String message;
  private final @LogValue String traceId;
  private final @LogValue List<FieldError> fieldErrors;

  @com.fasterxml.jackson.annotation.JsonCreator
  public ApiError(
      @com.fasterxml.jackson.annotation.JsonProperty("code") String code,
      @com.fasterxml.jackson.annotation.JsonProperty("message") String message,
      @com.fasterxml.jackson.annotation.JsonProperty("traceId") String traceId,
      @com.fasterxml.jackson.annotation.JsonProperty("fieldErrors") List<FieldError> fieldErrors) {

    this.code = code;
    this.message = message;
    this.traceId = traceId;
    this.fieldErrors = fieldErrors;
  }

  public String code() {
    return code;
  }

  public String getCode() {
    return code;
  }

  public String message() {
    return message;
  }

  public String getMessage() {
    return message;
  }

  public String traceId() {
    return traceId;
  }

  public String getTraceId() {
    return traceId;
  }

  public List<FieldError> fieldErrors() {
    return fieldErrors;
  }

  public List<FieldError> getFieldErrors() {
    return fieldErrors;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) return true;
    if (other == null || getClass() != other.getClass()) return false;
    ApiError that = (ApiError) other;
    return java.util.Objects.equals(code, that.code)
        && java.util.Objects.equals(message, that.message)
        && java.util.Objects.equals(traceId, that.traceId)
        && java.util.Objects.equals(fieldErrors, that.fieldErrors);
  }

  @Override
  public int hashCode() {
    return java.util.Objects.hash(code, message, traceId, fieldErrors);
  }

  @LogPayload
  public static final class FieldError {
    private final @LogValue String field;
    private final @LogValue String code;

    @com.fasterxml.jackson.annotation.JsonCreator
    public FieldError(
        @com.fasterxml.jackson.annotation.JsonProperty("field") String field,
        @com.fasterxml.jackson.annotation.JsonProperty("code") String code) {

      this.field = field;
      this.code = code;
    }

    public String field() {
      return field;
    }

    public String getField() {
      return field;
    }

    public String code() {
      return code;
    }

    public String getCode() {
      return code;
    }

    @Override
    public boolean equals(Object other) {
      if (this == other) return true;
      if (other == null || getClass() != other.getClass()) return false;
      FieldError that = (FieldError) other;
      return java.util.Objects.equals(field, that.field)
          && java.util.Objects.equals(code, that.code);
    }

    @Override
    public int hashCode() {
      return java.util.Objects.hash(field, code);
    }
  }
}
