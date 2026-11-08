package com.example.insurance.claim;

import java.math.BigDecimal;
import javax.persistence.*;

@Entity
@Table(
    name = "claim_requests",
    uniqueConstraints =
        @UniqueConstraint(name = "uk_claim_idempotency", columnNames = "idempotency_key"))
public class ClaimRecord {
  @Id private String id;
  @Version private Long version;

  @Column(name = "idempotency_key", nullable = false, length = 64)
  private String idempotencyKey;

  @Column(nullable = false, length = 64)
  private String fingerprint;

  @Column(nullable = false, length = 40)
  private String policyNumber;

  @Column(nullable = false, precision = 14, scale = 2)
  private BigDecimal amount;

  @Column(nullable = false, length = 254)
  private String claimantEmail;

  @Column(nullable = false, length = 200)
  private String objectKey;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 40)
  private ClaimStatus status;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private NotificationStatus notificationStatus;

  @Column(length = 80)
  private String coreClaimId;

  @Column(length = 80)
  private String failureCode;

  protected ClaimRecord() {}

  public ClaimRecord(String id, String key, String fingerprint, ClaimRequest request) {
    this.id = id;
    this.idempotencyKey = key;
    this.fingerprint = fingerprint;
    this.policyNumber = request.policyNumber();
    this.amount = request.amount();
    this.claimantEmail = request.claimantEmail();
    this.objectKey = "claims/" + id + "/document";
    this.status = ClaimStatus.PROCESSING;
    this.notificationStatus = NotificationStatus.NOT_ATTEMPTED;
  }

  public String getId() {
    return id;
  }

  public String getFingerprint() {
    return fingerprint;
  }

  public String getPolicyNumber() {
    return policyNumber;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public String getClaimantEmail() {
    return claimantEmail;
  }

  public String getObjectKey() {
    return objectKey;
  }

  public ClaimStatus getStatus() {
    return status;
  }

  public NotificationStatus getNotificationStatus() {
    return notificationStatus;
  }

  public String getCoreClaimId() {
    return coreClaimId;
  }

  public String getFailureCode() {
    return failureCode;
  }

  public void transition(ClaimStatus status, String coreClaimId, String failureCode) {
    this.status = status;
    this.coreClaimId = coreClaimId;
    this.failureCode = failureCode;
  }

  public void notification(NotificationStatus status) {
    this.notificationStatus = status;
  }
}
