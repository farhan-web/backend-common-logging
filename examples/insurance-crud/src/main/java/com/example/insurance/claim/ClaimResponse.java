package com.example.insurance.claim;

import io.github.commonlogging.annotation.*;
import io.github.commonlogging.payload.MaskStrategy;
import java.math.BigDecimal;

@LogPayload
public final class ClaimResponse {
  private final @LogValue String id;
  private final @LogMask(strategy = MaskStrategy.LAST_FOUR) String policyNumber;
  private final @LogValue BigDecimal amount;
  private final @LogValue ClaimStatus status;
  private final @LogValue NotificationStatus notificationStatus;
  private final @LogValue String coreClaimId;
  private final @LogValue String failureCode;

  @com.fasterxml.jackson.annotation.JsonCreator
  public ClaimResponse(
      @com.fasterxml.jackson.annotation.JsonProperty("id") String id,
      @com.fasterxml.jackson.annotation.JsonProperty("policyNumber") String policyNumber,
      @com.fasterxml.jackson.annotation.JsonProperty("amount") BigDecimal amount,
      @com.fasterxml.jackson.annotation.JsonProperty("status") ClaimStatus status,
      @com.fasterxml.jackson.annotation.JsonProperty("notificationStatus")
          NotificationStatus notificationStatus,
      @com.fasterxml.jackson.annotation.JsonProperty("coreClaimId") String coreClaimId,
      @com.fasterxml.jackson.annotation.JsonProperty("failureCode") String failureCode) {

    this.id = id;
    this.policyNumber = policyNumber;
    this.amount = amount;
    this.status = status;
    this.notificationStatus = notificationStatus;
    this.coreClaimId = coreClaimId;
    this.failureCode = failureCode;
  }

  public String id() {
    return id;
  }

  public String getId() {
    return id;
  }

  public String policyNumber() {
    return policyNumber;
  }

  public String getPolicyNumber() {
    return policyNumber;
  }

  public BigDecimal amount() {
    return amount;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public ClaimStatus status() {
    return status;
  }

  public ClaimStatus getStatus() {
    return status;
  }

  public NotificationStatus notificationStatus() {
    return notificationStatus;
  }

  public NotificationStatus getNotificationStatus() {
    return notificationStatus;
  }

  public String coreClaimId() {
    return coreClaimId;
  }

  public String getCoreClaimId() {
    return coreClaimId;
  }

  public String failureCode() {
    return failureCode;
  }

  public String getFailureCode() {
    return failureCode;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) return true;
    if (other == null || getClass() != other.getClass()) return false;
    ClaimResponse that = (ClaimResponse) other;
    return java.util.Objects.equals(id, that.id)
        && java.util.Objects.equals(policyNumber, that.policyNumber)
        && java.util.Objects.equals(amount, that.amount)
        && java.util.Objects.equals(status, that.status)
        && java.util.Objects.equals(notificationStatus, that.notificationStatus)
        && java.util.Objects.equals(coreClaimId, that.coreClaimId)
        && java.util.Objects.equals(failureCode, that.failureCode);
  }

  @Override
  public int hashCode() {
    return java.util.Objects.hash(
        id, policyNumber, amount, status, notificationStatus, coreClaimId, failureCode);
  }

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
