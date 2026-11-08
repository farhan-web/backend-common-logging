package com.example.insurance.claim;

import com.example.insurance.error.BusinessException;
import com.example.insurance.policy.*;
import io.github.commonlogging.annotation.LoggedOperation;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClaimPolicyValidator {
  private final PolicyRepository policies;

  public ClaimPolicyValidator(PolicyRepository policies) {
    this.policies = policies;
  }

  @Transactional(readOnly = true)
  @LoggedOperation("claim.policy.validateLocal")
  public void validate(
      @io.github.commonlogging.annotation.LogMask(
              strategy = io.github.commonlogging.payload.MaskStrategy.LAST_FOUR)
          String policyNumber) {
    Policy policy =
        policies
            .findByPolicyNumber(policyNumber)
            .orElseThrow(
                () ->
                    new BusinessException(
                        "POLICY_NOT_FOUND", "Policy was not found", HttpStatus.NOT_FOUND));
    if (policy.getStatus() != PolicyStatus.ACTIVE)
      throw new BusinessException(
          "POLICY_INACTIVE", "Policy is inactive", HttpStatus.UNPROCESSABLE_ENTITY);
  }
}
