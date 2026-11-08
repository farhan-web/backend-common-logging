package com.example.insurance.policy;

import io.github.commonlogging.annotation.*;
import io.github.commonlogging.payload.MaskStrategy;
import java.math.BigDecimal;

@LogPayload
public record PolicyResponse(
    @LogValue Long id,
    @LogMask(strategy = MaskStrategy.LAST_FOUR) String policyNumber,
    @LogMask String holderName,
    @LogMask String holderEmail,
    @LogValue BigDecimal premium,
    @LogValue PolicyStatus status) {
  public static PolicyResponse from(Policy policy) {
    return new PolicyResponse(
        policy.getId(),
        policy.getPolicyNumber(),
        policy.getHolderName(),
        policy.getHolderEmail(),
        policy.getPremium(),
        policy.getStatus());
  }
}
