package com.example.insurance.claim;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClaimRepository extends JpaRepository<ClaimRecord, String> {
  Optional<ClaimRecord> findByIdempotencyKey(String key);
}
