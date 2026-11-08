package com.example.insurance.error;

import io.github.commonlogging.api.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;

@Component
public class ApiErrorClassifier implements ErrorClassifier {
  @Override
  public ErrorDetails classify(Throwable error) {
    if (error instanceof com.example.insurance.integration.IntegrationException integration)
      return new ErrorDetails(
          "DEPENDENCY_" + integration.kind().name(),
          integration.code(),
          integration.kind()
              == com.example.insurance.integration.IntegrationException.Kind.REJECTED);
    if (error instanceof BusinessException business)
      return new ErrorDetails("BUSINESS", business.code(), true);
    if (error instanceof MethodArgumentNotValidException)
      return new ErrorDetails("VALIDATION", "INVALID_INPUT", true);
    if (error instanceof org.springframework.http.converter.HttpMessageNotReadableException
        || error instanceof org.springframework.beans.TypeMismatchException)
      return new ErrorDetails("VALIDATION", "INVALID_REQUEST", true);
    if (error instanceof DataIntegrityViolationException)
      return new ErrorDetails("BUSINESS", "DATA_CONFLICT", true);
    if (error instanceof ErrorResponse response && response.getStatusCode().is4xxClientError())
      return new ErrorDetails("REQUEST", "INVALID_REQUEST", true);
    return new ErrorDetails("INTERNAL", "INTERNAL_ERROR", false);
  }
}
