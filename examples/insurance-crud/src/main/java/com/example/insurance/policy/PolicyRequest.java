package com.example.insurance.policy;

import io.github.commonlogging.annotation.*;
import io.github.commonlogging.payload.MaskStrategy;
import java.math.BigDecimal;
import javax.validation.constraints.*;

@LogPayload
public final class PolicyRequest {
  private final @NotBlank @Size(max = 40) @Pattern(regexp = "[A-Za-z0-9-]+") @LogMask(
      strategy = MaskStrategy.LAST_FOUR) String policyNumber;
  private final @NotBlank @Size(max = 120) @LogMask String holderName;
  private final @NotBlank @Email @Size(max = 254) @LogMask String holderEmail;
  private final @LogValue @NotNull @DecimalMin("0.01") @Digits(integer = 12, fraction = 2)
  BigDecimal premium;
  private final @LogValue @NotNull PolicyStatus status;

  @com.fasterxml.jackson.annotation.JsonCreator
  public PolicyRequest(
      @com.fasterxml.jackson.annotation.JsonProperty("policyNumber") String policyNumber,
      @com.fasterxml.jackson.annotation.JsonProperty("holderName") String holderName,
      @com.fasterxml.jackson.annotation.JsonProperty("holderEmail") String holderEmail,
      @com.fasterxml.jackson.annotation.JsonProperty("premium") BigDecimal premium,
      @com.fasterxml.jackson.annotation.JsonProperty("status") PolicyStatus status) {

    this.policyNumber = policyNumber;
    this.holderName = holderName;
    this.holderEmail = holderEmail;
    this.premium = premium;
    this.status = status;
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
    PolicyRequest that = (PolicyRequest) other;
    return java.util.Objects.equals(policyNumber, that.policyNumber)
        && java.util.Objects.equals(holderName, that.holderName)
        && java.util.Objects.equals(holderEmail, that.holderEmail)
        && java.util.Objects.equals(premium, that.premium)
        && java.util.Objects.equals(status, that.status);
  }

  @Override
  public int hashCode() {
    return java.util.Objects.hash(policyNumber, holderName, holderEmail, premium, status);
  }
}
