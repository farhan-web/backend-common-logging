package com.example.insurance.claim;

import io.github.commonlogging.annotation.*;
import io.github.commonlogging.payload.MaskStrategy;
import java.math.BigDecimal;

@LogPayload
public record ClaimResponse(
    @LogValue String id,
    @LogMask(strategy = MaskStrategy.LAST_FOUR) String policyNumber,
    @LogValue BigDecimal amount,
    @LogValue ClaimStatus status,
    @LogValue NotificationStatus notificationStatus,
    @LogValue String coreClaimId,
    @LogValue String failureCode) {
  public static ClaimResponse from(ClaimRecord r) {
    return new ClaimResponse(
        r.getId(),
        r.getPolicyNumber(),
        r.getAmount(),
        r.getStatus(),
        r.getNotificationStatus(),
        r.getCoreClaimId(),
        r.getFailureCode());
  }
}
