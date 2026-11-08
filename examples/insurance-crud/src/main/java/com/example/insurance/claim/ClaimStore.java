package com.example.insurance.claim;

import com.example.insurance.error.BusinessException;
import io.github.commonlogging.annotation.LoggedOperation;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class ClaimStore {
  private final ClaimRepository repository;

  public ClaimStore(ClaimRepository repository) {
    this.repository = repository;
  }

  @Transactional(readOnly = true)
  @LoggedOperation("claim.database.findByKey")
  public Optional<ClaimRecord> findByKey(String key) {
    return repository.findByIdempotencyKey(key);
  }

  @Transactional(readOnly = true)
  @LoggedOperation("claim.database.get")
  public ClaimRecord get(String id) {
    return find(id);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  @LoggedOperation("claim.database.create")
  public ClaimRecord create(String id, String key, String fingerprint, ClaimRequest request) {
    return repository.saveAndFlush(new ClaimRecord(id, key, fingerprint, request));
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  @LoggedOperation("claim.database.transition")
  public ClaimRecord transition(String id, ClaimStatus status, String coreId, String code) {
    ClaimRecord claim = find(id);
    claim.transition(status, coreId, code);
    return repository.saveAndFlush(claim);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  @LoggedOperation("claim.database.notification")
  public ClaimRecord notification(String id, NotificationStatus status) {
    ClaimRecord claim = find(id);
    claim.notification(status);
    return repository.saveAndFlush(claim);
  }

  private ClaimRecord find(String id) {
    return repository
        .findById(id)
        .orElseThrow(
            () ->
                new BusinessException(
                    "CLAIM_NOT_FOUND", "Claim was not found", HttpStatus.NOT_FOUND));
  }
}
