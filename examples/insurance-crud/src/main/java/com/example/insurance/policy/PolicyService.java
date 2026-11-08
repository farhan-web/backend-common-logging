package com.example.insurance.policy;

import com.example.insurance.error.BusinessException;
import io.github.commonlogging.annotation.LoggedOperation;
import io.github.commonlogging.api.TLog;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PolicyService {
  private final PolicyRepository repository;
  private final TLog tLog;

  public PolicyService(PolicyRepository repository, TLog tLog) {
    this.repository = repository;
    this.tLog = tLog;
  }

  @Transactional
  @LoggedOperation("policy.create")
  public PolicyResponse create(PolicyRequest request) {
    if (repository.existsByPolicyNumber(request.policyNumber())) throw duplicate();
    tLog.info("policy.create.validation", "Policy number uniqueness check passed");
    return PolicyResponse.from(repository.saveAndFlush(new Policy(request)));
  }

  @LoggedOperation("policy.list")
  public PoliciesResponse list() {
    return new PoliciesResponse(
        repository.findAll(Sort.by("id")).stream()
            .map(PolicyResponse::from)
            .collect(java.util.stream.Collectors.toList()));
  }

  @LoggedOperation("policy.get")
  public PolicyResponse get(@io.github.commonlogging.annotation.LogValue("policyId") Long id) {
    return PolicyResponse.from(find(id));
  }

  @Transactional
  @LoggedOperation("policy.update")
  public PolicyResponse update(Long id, PolicyRequest request) {
    Policy policy = find(id);
    if (repository.existsByPolicyNumberAndIdNot(request.policyNumber(), id)) throw duplicate();
    policy.update(request);
    return PolicyResponse.from(repository.saveAndFlush(policy));
  }

  @Transactional
  @LoggedOperation("policy.delete")
  public void delete(Long id) {
    repository.delete(find(id));
    repository.flush();
  }

  private Policy find(Long id) {
    return repository
        .findById(id)
        .orElseThrow(
            () ->
                new BusinessException(
                    "POLICY_NOT_FOUND", "Policy was not found", HttpStatus.NOT_FOUND));
  }

  private BusinessException duplicate() {
    return new BusinessException(
        "POLICY_NUMBER_ALREADY_EXISTS", "Policy number already exists", HttpStatus.CONFLICT);
  }
}
