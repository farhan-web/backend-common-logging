package com.example.insurance.claim;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import java.net.URI;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/claims")
public class ClaimController {
  private final ClaimSubmissionService service;

  public ClaimController(ClaimSubmissionService service) {
    this.service = service;
  }

  @PostMapping
  public ResponseEntity<ClaimResponse> submit(
      @RequestHeader("Idempotency-Key") @Pattern(regexp = "[A-Za-z0-9._-]{1,64}")
          String idempotencyKey,
      @Valid @RequestBody ClaimRequest request) {
    var result = service.submit(idempotencyKey, request);
    ClaimResponse response = result.claim();
    boolean pending =
        response.status() == ClaimStatus.PROCESSING
            || response.status() == ClaimStatus.PENDING_RECONCILIATION;
    return ResponseEntity.status(
            pending ? HttpStatus.ACCEPTED : result.replay() ? HttpStatus.OK : HttpStatus.CREATED)
        .location(URI.create("/api/claims/" + response.id()))
        .body(response);
  }

  @GetMapping("/{id}")
  public ClaimResponse get(@PathVariable @Pattern(regexp = "[0-9a-fA-F-]{36}") String id) {
    return service.get(id);
  }

  @PostMapping("/{id}/reconcile")
  public ClaimResponse reconcile(@PathVariable @Pattern(regexp = "[0-9a-fA-F-]{36}") String id) {
    return service.reconcile(id);
  }
}
