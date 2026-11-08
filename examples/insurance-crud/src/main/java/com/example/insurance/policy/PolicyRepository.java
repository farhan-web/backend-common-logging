package com.example.insurance.policy;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PolicyRepository extends JpaRepository<Policy, Long> {
  java.util.Optional<Policy> findByPolicyNumber(String policyNumber);

  boolean existsByPolicyNumber(String policyNumber);

  boolean existsByPolicyNumberAndIdNot(String policyNumber, Long id);
}
