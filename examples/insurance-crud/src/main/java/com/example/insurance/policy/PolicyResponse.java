package com.example.insurance.policy;

import io.github.commonlogging.annotation.*;
import io.github.commonlogging.payload.MaskStrategy;
import java.math.BigDecimal;

@LogPayload
public final class PolicyResponse {
  private final @LogValue Long id;
  private final @LogMask(strategy = MaskStrategy.LAST_FOUR) String policyNumber;
  private final @LogMask String holderName;
  private final @LogMask String holderEmail;
  private final @LogValue BigDecimal premium;
  private final @LogValue PolicyStatus status;

  @com.fasterxml.jackson.annotation.JsonCreator
  public PolicyResponse(
      @com.fasterxml.jackson.annotation.JsonProperty("id") Long id,
      @com.fasterxml.jackson.annotation.JsonProperty("policyNumber") String policyNumber,
      @com.fasterxml.jackson.annotation.JsonProperty("holderName") String holderName,
      @com.fasterxml.jackson.annotation.JsonProperty("holderEmail") String holderEmail,
      @com.fasterxml.jackson.annotation.JsonProperty("premium") BigDecimal premium,
      @com.fasterxml.jackson.annotation.JsonProperty("status") PolicyStatus status) {

    this.id = id;
    this.policyNumber = policyNumber;
    this.holderName = holderName;
    this.holderEmail = holderEmail;
    this.premium = premium;
    this.status = status;
  }

  public Long id() {
    return id;
  }

  public Long getId() {
    return id;
  }

  public String policyNumber() {
    return policyNumber;
  }

  public String getPolicyNumber() {
    return policyNumber;
  }

  public String holderName() {
    return holderName;
  }

  public String getHolderName() {
    return holderName;
  }

  public String holderEmail() {
    return holderEmail;
  }

  public String getHolderEmail() {
    return holderEmail;
  }

  public BigDecimal premium() {
    return premium;
  }

  public BigDecimal getPremium() {
    return premium;
  }

  public PolicyStatus status() {
    return status;
  }

  public PolicyStatus getStatus() {
    return status;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) return true;
    if (other == null || getClass() != other.getClass()) return false;
    PolicyResponse that = (PolicyResponse) other;
    return java.util.Objects.equals(id, that.id)
        && java.util.Objects.equals(policyNumber, that.policyNumber)
        && java.util.Objects.equals(holderName, that.holderName)
        && java.util.Objects.equals(holderEmail, that.holderEmail)
        && java.util.Objects.equals(premium, that.premium)
        && java.util.Objects.equals(status, that.status);
  }

  @Override
  public int hashCode() {
    return java.util.Objects.hash(id, policyNumber, holderName, holderEmail, premium, status);
  }

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
