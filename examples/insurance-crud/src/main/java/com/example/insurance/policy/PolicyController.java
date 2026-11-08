package com.example.insurance.policy;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/policies")
public class PolicyController {
  private final PolicyService service;

  public PolicyController(PolicyService service) {
    this.service = service;
  }

  @PostMapping
  public ResponseEntity<PolicyResponse> create(@Valid @RequestBody PolicyRequest request) {
    PolicyResponse response = service.create(request);
    return ResponseEntity.created(URI.create("/api/policies/" + response.id())).body(response);
  }

  @GetMapping
  public PoliciesResponse list() {
    return service.list();
  }

  @GetMapping("/{id}")
  public PolicyResponse get(@PathVariable @Positive Long id) {
    return service.get(id);
  }

  @PutMapping("/{id}")
  public PolicyResponse update(
      @PathVariable @Positive Long id, @Valid @RequestBody PolicyRequest request) {
    return service.update(id, request);
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable @Positive Long id) {
    service.delete(id);
    return ResponseEntity.noContent().build();
  }
}
