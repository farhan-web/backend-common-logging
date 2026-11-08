package com.example.insurance.claim;

import com.example.insurance.error.BusinessException;
import com.example.insurance.integration.*;
import com.example.insurance.integration.IntegrationDtos.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.commonlogging.annotation.LoggedOperation;
import io.github.commonlogging.api.*;
import java.security.*;
import java.util.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** No database transaction spans network calls; state writes commit in ClaimStore. */
@Service
public class ClaimSubmissionService {
  @io.github.commonlogging.annotation.LogPayload
  public record Submission(
      @io.github.commonlogging.annotation.LogValue ClaimResponse claim,
      @io.github.commonlogging.annotation.LogValue boolean replay) {}

  private final ClaimStore store;
  private final ClaimPolicyValidator validator;
  private final CoreInsuranceClient core;
  private final S3DocumentStorage documents;
  private final NotificationClient notifications;
  private final TLog log;
  private final TExceptionLog errors;
  private final ObjectMapper mapper;

  public ClaimSubmissionService(
      ClaimStore store,
      ClaimPolicyValidator validator,
      CoreInsuranceClient core,
      S3DocumentStorage documents,
      NotificationClient notifications,
      TLog log,
      TExceptionLog errors,
      ObjectMapper mapper) {
    this.store = store;
    this.validator = validator;
    this.core = core;
    this.documents = documents;
    this.notifications = notifications;
    this.log = log;
    this.errors = errors;
    this.mapper = mapper;
  }

  @LoggedOperation("claim.submit")
  public Submission submit(
      @io.github.commonlogging.annotation.LogExclude String idempotencyKey, ClaimRequest request) {
    byte[] bytes = document(request.documentBase64());
    String fingerprint = fingerprint(request);
    Optional<ClaimRecord> existing = store.findByKey(idempotencyKey);
    if (existing.isPresent()) return replay(existing.get(), fingerprint);
    ClaimRecord record;
    try {
      record = store.create(UUID.randomUUID().toString(), idempotencyKey, fingerprint, request);
    } catch (DataIntegrityViolationException race) {
      Optional<ClaimRecord> winner = store.findByKey(idempotencyKey);
      if (winner.isEmpty()) throw race;
      log.recovered(race);
      return replay(winner.get(), fingerprint);
    }
    boolean uploadAttempted = false;
    try {
      validator.validate(request.policyNumber());
      VerifyResponse eligibility =
          core.verify(new VerifyRequest(request.policyNumber(), request.amount()));
      if (!eligibility.eligible() || request.amount().compareTo(eligibility.coverageLimit()) > 0)
        throw new BusinessException(
            "CLAIM_NOT_ELIGIBLE",
            "Claim does not meet policy coverage",
            HttpStatus.UNPROCESSABLE_ENTITY);
      uploadAttempted = true;
      documents.put(record.getObjectKey(), request.contentType(), bytes);
    } catch (RuntimeException primary) {
      boolean cleaned = !uploadAttempted || compensate(record.getObjectKey());
      boolean uncertainUpload =
          uploadAttempted
              && primary instanceof IntegrationException integration
              && (integration.kind() == IntegrationException.Kind.TIMEOUT
                  || integration.kind() == IntegrationException.Kind.UNAVAILABLE);
      boolean rejected =
          primary instanceof BusinessException
              || primary instanceof IntegrationException integration
                  && integration.kind() == IntegrationException.Kind.REJECTED;
      store.transition(
          record.getId(),
          cleaned && !uncertainUpload
              ? (rejected ? ClaimStatus.REJECTED : ClaimStatus.FAILED)
              : ClaimStatus.COMPENSATION_REQUIRED,
          null,
          code(primary));
      throw primary;
    }
    CoreClaimResponse accepted;
    try {
      accepted =
          core.submit(
              new CoreClaimRequest(
                  record.getId(),
                  record.getPolicyNumber(),
                  record.getAmount(),
                  record.getObjectKey()));
    } catch (IntegrationException primary) {
      if (primary.kind() == IntegrationException.Kind.REJECTED) {
        boolean cleaned = compensate(record.getObjectKey());
        store.transition(
            record.getId(),
            cleaned ? ClaimStatus.REJECTED : ClaimStatus.COMPENSATION_REQUIRED,
            null,
            primary.code());
        throw primary;
      }
      // A timeout/5xx/malformed response after POST does not prove the core rejected the claim.
      ClaimRecord pending =
          store.transition(
              record.getId(), ClaimStatus.PENDING_RECONCILIATION, null, primary.code());
      errors.error("claim.awaitReconciliation", primary);
      log.recovered(primary);
      log.info(
          "claim.awaitReconciliation",
          "Core submission outcome is uncertain; preserve document for reconciliation");
      return new Submission(ClaimResponse.from(pending), false);
    } catch (RuntimeException unexpected) {
      store.transition(
          record.getId(), ClaimStatus.PENDING_RECONCILIATION, null, "CORE_OUTCOME_UNKNOWN");
      throw unexpected;
    }
    ClaimRecord submitted =
        store.transition(record.getId(), ClaimStatus.SUBMITTED, accepted.coreClaimId(), null);
    return new Submission(ClaimResponse.from(notifyBestEffort(submitted)), false);
  }

  @LoggedOperation("claim.get")
  public ClaimResponse get(String id) {
    return ClaimResponse.from(store.get(id));
  }

  @LoggedOperation("claim.reconcile")
  public ClaimResponse reconcile(String id) {
    ClaimRecord record = store.get(id);
    if (record.getStatus() == ClaimStatus.PENDING_RECONCILIATION) {
      CoreClaimResponse found = core.lookup(id);
      if (found == null) {
        log.info(
            "claim.reconcile.notFound",
            "Core record not visible; keep pending without resubmission or document deletion");
        return ClaimResponse.from(record);
      }
      record = store.transition(id, ClaimStatus.SUBMITTED, found.coreClaimId(), null);
    }
    if (record.getStatus() == ClaimStatus.SUBMITTED
        && record.getNotificationStatus() != NotificationStatus.SENT)
      record = notifyBestEffort(record);
    return ClaimResponse.from(record);
  }

  private ClaimRecord notifyBestEffort(ClaimRecord record) {
    try {
      notifications.send(new NotificationRequest(record.getId(), record.getClaimantEmail()));
    } catch (IntegrationException failure) {
      ClaimRecord pending = store.notification(record.getId(), NotificationStatus.PENDING);
      errors.error("claim.notification.recovery", failure);
      log.recovered(failure);
      log.info("claim.notification.recovery", "Claim accepted; notification remains pending");
      return pending;
    }
    return store.notification(record.getId(), NotificationStatus.SENT);
  }

  private boolean compensate(String key) {
    try {
      documents.delete(key);
      return true;
    } catch (IntegrationException cleanup) {
      errors.error("claim.compensation", cleanup);
      log.recovered(cleanup);
      log.info("claim.compensation", "Document cleanup failed; operator reconciliation required");
      return false;
    }
  }

  private Submission replay(ClaimRecord record, String fingerprint) {
    if (!record.getFingerprint().equals(fingerprint))
      throw new BusinessException(
          "IDEMPOTENCY_KEY_REUSED",
          "Idempotency key was used for a different request",
          HttpStatus.CONFLICT);
    log.info(
        "claim.idempotency.replay", "Return recorded claim without calling dependencies again");
    return new Submission(ClaimResponse.from(record), true);
  }

  private byte[] document(String encoded) {
    try {
      byte[] bytes = Base64.getDecoder().decode(encoded);
      if (bytes.length == 0 || bytes.length > 1048576) throw new IllegalArgumentException();
      return bytes;
    } catch (IllegalArgumentException invalid) {
      throw new BusinessException(
          "INVALID_DOCUMENT",
          "Document must be valid Base64 containing 1 to 1048576 bytes",
          HttpStatus.BAD_REQUEST);
    }
  }

  private String fingerprint(ClaimRequest request) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(request)));
    } catch (Exception error) {
      throw new IllegalStateException("Could not fingerprint claim request", error);
    }
  }

  private String code(RuntimeException error) {
    return error instanceof BusinessException business
        ? business.code()
        : error instanceof IntegrationException integration ? integration.code() : "INTERNAL_ERROR";
  }
}
