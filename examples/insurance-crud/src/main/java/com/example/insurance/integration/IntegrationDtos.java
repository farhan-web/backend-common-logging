package com.example.insurance.integration;

import io.github.commonlogging.annotation.*;
import io.github.commonlogging.payload.MaskStrategy;
import java.math.BigDecimal;

public final class IntegrationDtos {
  private IntegrationDtos() {}

  @LogPayload
  public record VerifyRequest(
      @LogMask(strategy = MaskStrategy.LAST_FOUR) String policyNumber,
      @LogValue BigDecimal amount) {}

  @LogPayload
  public record VerifyResponse(@LogValue boolean eligible, @LogValue BigDecimal coverageLimit) {}

  @LogPayload
  public record CoreClaimRequest(
      @LogValue String externalReference,
      @LogMask(strategy = MaskStrategy.LAST_FOUR) String policyNumber,
      @LogValue BigDecimal amount,
      @LogValue String documentKey) {}

  @LogPayload
  public record CoreClaimResponse(
      @LogValue String coreClaimId, @LogValue String externalReference, @LogValue String status) {}

  @LogPayload
  public record NotificationRequest(@LogValue String claimId, @LogMask String email) {}

  @LogPayload
  public record Receipt(@LogValue String result) {}

  @LogPayload
  public record DocumentMetadata(
      @LogValue String bucket,
      @LogValue String objectKey,
      @LogValue String contentType,
      @LogValue int bytes) {}

  @LogPayload
  public record ObjectReference(@LogValue String objectKey) {}
}
