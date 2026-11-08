package com.example.insurance.error;

import io.github.commonlogging.annotation.LogPayload;
import io.github.commonlogging.annotation.LogValue;
import java.util.List;

@LogPayload
public record ApiError(
    @LogValue String code,
    @LogValue String message,
    @LogValue String traceId,
    @LogValue List<FieldError> fieldErrors) {
  @LogPayload
  public record FieldError(@LogValue String field, @LogValue String code) {}
}
