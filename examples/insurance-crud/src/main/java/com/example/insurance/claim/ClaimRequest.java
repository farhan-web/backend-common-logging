package com.example.insurance.claim;

import io.github.commonlogging.annotation.*;
import io.github.commonlogging.payload.MaskStrategy;
import java.math.BigDecimal;
import javax.validation.constraints.*;

@LogPayload
public final class ClaimRequest {
  private final @NotBlank @Size(max = 40) @LogMask(strategy = MaskStrategy.LAST_FOUR) String
      policyNumber;
  private final @LogValue @NotNull @DecimalMin("0.01") @Digits(integer = 12, fraction = 2)
  BigDecimal amount;
  private final @NotBlank @Email @Size(max = 254) @LogMask String claimantEmail;
  private final @NotBlank @Size(max = 1000) @LogExclude String description;
  private final @LogValue @NotBlank @Pattern(regexp = "application/pdf|text/plain") String
      contentType;
  private final @NotBlank @Size(max = 1400000) @LogExclude String documentBase64;

  @com.fasterxml.jackson.annotation.JsonCreator
  public ClaimRequest(
      @com.fasterxml.jackson.annotation.JsonProperty("policyNumber") String policyNumber,
      @com.fasterxml.jackson.annotation.JsonProperty("amount") BigDecimal amount,
      @com.fasterxml.jackson.annotation.JsonProperty("claimantEmail") String claimantEmail,
      @com.fasterxml.jackson.annotation.JsonProperty("description") String description,
      @com.fasterxml.jackson.annotation.JsonProperty("contentType") String contentType,
      @com.fasterxml.jackson.annotation.JsonProperty("documentBase64") String documentBase64) {

    this.policyNumber = policyNumber;
    this.amount = amount;
    this.claimantEmail = claimantEmail;
    this.description = description;
    this.contentType = contentType;
    this.documentBase64 = documentBase64;
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

  public String claimantEmail() {
    return claimantEmail;
  }

  public String getClaimantEmail() {
    return claimantEmail;
  }

  public String description() {
    return description;
  }

  public String getDescription() {
    return description;
  }

  public String contentType() {
    return contentType;
  }

  public String getContentType() {
    return contentType;
  }

  public String documentBase64() {
    return documentBase64;
  }

  public String getDocumentBase64() {
    return documentBase64;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) return true;
    if (other == null || getClass() != other.getClass()) return false;
    ClaimRequest that = (ClaimRequest) other;
    return java.util.Objects.equals(policyNumber, that.policyNumber)
        && java.util.Objects.equals(amount, that.amount)
        && java.util.Objects.equals(claimantEmail, that.claimantEmail)
        && java.util.Objects.equals(description, that.description)
        && java.util.Objects.equals(contentType, that.contentType)
        && java.util.Objects.equals(documentBase64, that.documentBase64);
  }

  @Override
  public int hashCode() {
    return java.util.Objects.hash(
        policyNumber, amount, claimantEmail, description, contentType, documentBase64);
  }
}
