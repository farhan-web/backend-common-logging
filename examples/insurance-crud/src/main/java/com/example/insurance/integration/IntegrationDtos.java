package com.example.insurance.integration;

import io.github.commonlogging.annotation.*;
import io.github.commonlogging.payload.MaskStrategy;
import java.math.BigDecimal;

public final class IntegrationDtos {
  private IntegrationDtos() {}

  @LogPayload
  public static final class VerifyRequest {
    private final @LogMask(strategy = MaskStrategy.LAST_FOUR) String policyNumber;
    private final @LogValue BigDecimal amount;

    @com.fasterxml.jackson.annotation.JsonCreator
    public VerifyRequest(
        @com.fasterxml.jackson.annotation.JsonProperty("policyNumber") String policyNumber,
        @com.fasterxml.jackson.annotation.JsonProperty("amount") BigDecimal amount) {

      this.policyNumber = policyNumber;
      this.amount = amount;
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

    @Override
    public boolean equals(Object other) {
      if (this == other) return true;
      if (other == null || getClass() != other.getClass()) return false;
      VerifyRequest that = (VerifyRequest) other;
      return java.util.Objects.equals(policyNumber, that.policyNumber)
          && java.util.Objects.equals(amount, that.amount);
    }

    @Override
    public int hashCode() {
      return java.util.Objects.hash(policyNumber, amount);
    }
  }

  @LogPayload
  public static final class VerifyResponse {
    private final @LogValue boolean eligible;
    private final @LogValue BigDecimal coverageLimit;

    @com.fasterxml.jackson.annotation.JsonCreator
    public VerifyResponse(
        @com.fasterxml.jackson.annotation.JsonProperty("eligible") boolean eligible,
        @com.fasterxml.jackson.annotation.JsonProperty("coverageLimit") BigDecimal coverageLimit) {

      this.eligible = eligible;
      this.coverageLimit = coverageLimit;
    }

    public boolean eligible() {
      return eligible;
    }

    public boolean getEligible() {
      return eligible;
    }

    public BigDecimal coverageLimit() {
      return coverageLimit;
    }

    public BigDecimal getCoverageLimit() {
      return coverageLimit;
    }

    @Override
    public boolean equals(Object other) {
      if (this == other) return true;
      if (other == null || getClass() != other.getClass()) return false;
      VerifyResponse that = (VerifyResponse) other;
      return java.util.Objects.equals(eligible, that.eligible)
          && java.util.Objects.equals(coverageLimit, that.coverageLimit);
    }

    @Override
    public int hashCode() {
      return java.util.Objects.hash(eligible, coverageLimit);
    }
  }

  @LogPayload
  public static final class CoreClaimRequest {
    private final @LogValue String externalReference;
    private final @LogMask(strategy = MaskStrategy.LAST_FOUR) String policyNumber;
    private final @LogValue BigDecimal amount;
    private final @LogValue String documentKey;

    @com.fasterxml.jackson.annotation.JsonCreator
    public CoreClaimRequest(
        @com.fasterxml.jackson.annotation.JsonProperty("externalReference")
            String externalReference,
        @com.fasterxml.jackson.annotation.JsonProperty("policyNumber") String policyNumber,
        @com.fasterxml.jackson.annotation.JsonProperty("amount") BigDecimal amount,
        @com.fasterxml.jackson.annotation.JsonProperty("documentKey") String documentKey) {

      this.externalReference = externalReference;
      this.policyNumber = policyNumber;
      this.amount = amount;
      this.documentKey = documentKey;
    }

    public String externalReference() {
      return externalReference;
    }

    public String getExternalReference() {
      return externalReference;
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

    public String documentKey() {
      return documentKey;
    }

    public String getDocumentKey() {
      return documentKey;
    }

    @Override
    public boolean equals(Object other) {
      if (this == other) return true;
      if (other == null || getClass() != other.getClass()) return false;
      CoreClaimRequest that = (CoreClaimRequest) other;
      return java.util.Objects.equals(externalReference, that.externalReference)
          && java.util.Objects.equals(policyNumber, that.policyNumber)
          && java.util.Objects.equals(amount, that.amount)
          && java.util.Objects.equals(documentKey, that.documentKey);
    }

    @Override
    public int hashCode() {
      return java.util.Objects.hash(externalReference, policyNumber, amount, documentKey);
    }
  }

  @LogPayload
  public static final class CoreClaimResponse {
    private final @LogValue String coreClaimId;
    private final @LogValue String externalReference;
    private final @LogValue String status;

    @com.fasterxml.jackson.annotation.JsonCreator
    public CoreClaimResponse(
        @com.fasterxml.jackson.annotation.JsonProperty("coreClaimId") String coreClaimId,
        @com.fasterxml.jackson.annotation.JsonProperty("externalReference")
            String externalReference,
        @com.fasterxml.jackson.annotation.JsonProperty("status") String status) {

      this.coreClaimId = coreClaimId;
      this.externalReference = externalReference;
      this.status = status;
    }

    public String coreClaimId() {
      return coreClaimId;
    }

    public String getCoreClaimId() {
      return coreClaimId;
    }

    public String externalReference() {
      return externalReference;
    }

    public String getExternalReference() {
      return externalReference;
    }

    public String status() {
      return status;
    }

    public String getStatus() {
      return status;
    }

    @Override
    public boolean equals(Object other) {
      if (this == other) return true;
      if (other == null || getClass() != other.getClass()) return false;
      CoreClaimResponse that = (CoreClaimResponse) other;
      return java.util.Objects.equals(coreClaimId, that.coreClaimId)
          && java.util.Objects.equals(externalReference, that.externalReference)
          && java.util.Objects.equals(status, that.status);
    }

    @Override
    public int hashCode() {
      return java.util.Objects.hash(coreClaimId, externalReference, status);
    }
  }

  @LogPayload
  public static final class NotificationRequest {
    private final @LogValue String claimId;
    private final @LogMask String email;

    @com.fasterxml.jackson.annotation.JsonCreator
    public NotificationRequest(
        @com.fasterxml.jackson.annotation.JsonProperty("claimId") String claimId,
        @com.fasterxml.jackson.annotation.JsonProperty("email") String email) {

      this.claimId = claimId;
      this.email = email;
    }

    public String claimId() {
      return claimId;
    }

    public String getClaimId() {
      return claimId;
    }

    public String email() {
      return email;
    }

    public String getEmail() {
      return email;
    }

    @Override
    public boolean equals(Object other) {
      if (this == other) return true;
      if (other == null || getClass() != other.getClass()) return false;
      NotificationRequest that = (NotificationRequest) other;
      return java.util.Objects.equals(claimId, that.claimId)
          && java.util.Objects.equals(email, that.email);
    }

    @Override
    public int hashCode() {
      return java.util.Objects.hash(claimId, email);
    }
  }

  @LogPayload
  public static final class Receipt {
    private final @LogValue String result;

    @com.fasterxml.jackson.annotation.JsonCreator
    public Receipt(@com.fasterxml.jackson.annotation.JsonProperty("result") String result) {

      this.result = result;
    }

    public String result() {
      return result;
    }

    public String getResult() {
      return result;
    }

    @Override
    public boolean equals(Object other) {
      if (this == other) return true;
      if (other == null || getClass() != other.getClass()) return false;
      Receipt that = (Receipt) other;
      return java.util.Objects.equals(result, that.result);
    }

    @Override
    public int hashCode() {
      return java.util.Objects.hash(result);
    }
  }

  @LogPayload
  public static final class DocumentMetadata {
    private final @LogValue String bucket;
    private final @LogValue String objectKey;
    private final @LogValue String contentType;
    private final @LogValue int bytes;

    @com.fasterxml.jackson.annotation.JsonCreator
    public DocumentMetadata(
        @com.fasterxml.jackson.annotation.JsonProperty("bucket") String bucket,
        @com.fasterxml.jackson.annotation.JsonProperty("objectKey") String objectKey,
        @com.fasterxml.jackson.annotation.JsonProperty("contentType") String contentType,
        @com.fasterxml.jackson.annotation.JsonProperty("bytes") int bytes) {

      this.bucket = bucket;
      this.objectKey = objectKey;
      this.contentType = contentType;
      this.bytes = bytes;
    }

    public String bucket() {
      return bucket;
    }

    public String getBucket() {
      return bucket;
    }

    public String objectKey() {
      return objectKey;
    }

    public String getObjectKey() {
      return objectKey;
    }

    public String contentType() {
      return contentType;
    }

    public String getContentType() {
      return contentType;
    }

    public int bytes() {
      return bytes;
    }

    public int getBytes() {
      return bytes;
    }

    @Override
    public boolean equals(Object other) {
      if (this == other) return true;
      if (other == null || getClass() != other.getClass()) return false;
      DocumentMetadata that = (DocumentMetadata) other;
      return java.util.Objects.equals(bucket, that.bucket)
          && java.util.Objects.equals(objectKey, that.objectKey)
          && java.util.Objects.equals(contentType, that.contentType)
          && java.util.Objects.equals(bytes, that.bytes);
    }

    @Override
    public int hashCode() {
      return java.util.Objects.hash(bucket, objectKey, contentType, bytes);
    }
  }

  @LogPayload
  public static final class ObjectReference {
    private final @LogValue String objectKey;

    @com.fasterxml.jackson.annotation.JsonCreator
    public ObjectReference(
        @com.fasterxml.jackson.annotation.JsonProperty("objectKey") String objectKey) {

      this.objectKey = objectKey;
    }

    public String objectKey() {
      return objectKey;
    }

    public String getObjectKey() {
      return objectKey;
    }

    @Override
    public boolean equals(Object other) {
      if (this == other) return true;
      if (other == null || getClass() != other.getClass()) return false;
      ObjectReference that = (ObjectReference) other;
      return java.util.Objects.equals(objectKey, that.objectKey);
    }

    @Override
    public int hashCode() {
      return java.util.Objects.hash(objectKey);
    }
  }
}
