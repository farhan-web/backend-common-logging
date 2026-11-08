package com.example.insurance.policy;

import io.github.commonlogging.annotation.LogPayload;
import io.github.commonlogging.annotation.LogValue;
import java.util.List;

@LogPayload
public final class PoliciesResponse {
  private final @LogValue List<PolicyResponse> items;

  @com.fasterxml.jackson.annotation.JsonCreator
  public PoliciesResponse(
      @com.fasterxml.jackson.annotation.JsonProperty("items") List<PolicyResponse> items) {

    this.items = items;
  }

  public List<PolicyResponse> items() {
    return items;
  }

  public List<PolicyResponse> getItems() {
    return items;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) return true;
    if (other == null || getClass() != other.getClass()) return false;
    PoliciesResponse that = (PoliciesResponse) other;
    return java.util.Objects.equals(items, that.items);
  }

  @Override
  public int hashCode() {
    return java.util.Objects.hash(items);
  }
}
