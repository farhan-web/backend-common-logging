package com.example.insurance.error;

import io.github.commonlogging.api.TLog;
import io.github.commonlogging.spring.ExceptionLoggingBridge;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
  private final ExceptionLoggingBridge errors;
  private final TLog tLog;

  public GlobalExceptionHandler(ExceptionLoggingBridge errors, TLog tLog) {
    this.errors = errors;
    this.tLog = tLog;
  }

  @ExceptionHandler(javax.validation.ConstraintViolationException.class)
  ResponseEntity<ApiError> invalidParameter(javax.validation.ConstraintViolationException error) {
    errors.log(error);
    return ResponseEntity.badRequest()
        .body(
            body(
                "INVALID_REQUEST",
                "Request parameter validation failed",
                java.util.Collections.emptyList()));
  }

  @ExceptionHandler(BusinessException.class)
  ResponseEntity<ApiError> business(BusinessException error) {
    errors.log(error);
    return ResponseEntity.status(error.status())
        .body(
            body(
                error.code(),
                error.getMessage(),
                io.github.commonlogging.internal.Java8Collections.list()));
  }

  @ExceptionHandler(com.example.insurance.integration.IntegrationException.class)
  ResponseEntity<ApiError> integration(
      com.example.insurance.integration.IntegrationException error) {
    errors.log(error);
    String message =
        error.kind() == com.example.insurance.integration.IntegrationException.Kind.REJECTED
            ? "Core insurance rejected the claim"
            : "An integration could not complete the operation";
    return ResponseEntity.status(error.status())
        .body(
            body(error.code(), message, io.github.commonlogging.internal.Java8Collections.list()));
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<ApiError> conflict(DataIntegrityViolationException error) {
    errors.log(error);
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(
            body(
                "DATA_CONFLICT",
                "Data conflicts with an existing policy",
                io.github.commonlogging.internal.Java8Collections.list()));
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ApiError> unexpected(Exception error) {
    errors.log(error);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(
            body(
                "INTERNAL_ERROR",
                "An unexpected error occurred",
                io.github.commonlogging.internal.Java8Collections.list()));
  }

  @Override
  protected ResponseEntity<Object> handleExceptionInternal(
      Exception error,
      Object ignoredBody,
      HttpHeaders headers,
      HttpStatus status,
      WebRequest request) {
    errors.log(error);
    List<ApiError.FieldError> fields =
        error instanceof MethodArgumentNotValidException
            ? ((MethodArgumentNotValidException) error)
                .getBindingResult().getFieldErrors().stream()
                    .map(
                        field ->
                            new ApiError.FieldError(
                                field.getField(),
                                field.getCode() == null ? "Invalid" : field.getCode()))
                    .collect(java.util.stream.Collectors.toList())
            : io.github.commonlogging.internal.Java8Collections.list();
    String code =
        error instanceof MethodArgumentNotValidException
            ? "INVALID_INPUT"
            : status.is4xxClientError() ? "INVALID_REQUEST" : "INTERNAL_ERROR";
    String message =
        error instanceof MethodArgumentNotValidException
            ? "Request validation failed"
            : status.is4xxClientError()
                ? "Request could not be processed"
                : "An unexpected error occurred";
    return super.handleExceptionInternal(
        error, body(code, message, fields), headers, status, request);
  }

  private ApiError body(String code, String safeMessage, List<ApiError.FieldError> fields) {
    return new ApiError(code, safeMessage, tLog.traceId(), fields);
  }
}
