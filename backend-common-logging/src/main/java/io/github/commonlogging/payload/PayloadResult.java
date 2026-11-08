package io.github.commonlogging.payload;

import com.fasterxml.jackson.databind.JsonNode;

public final class PayloadResult {
  private final JsonNode payload;
  private final String status;
  private final String reason;

  @com.fasterxml.jackson.annotation.JsonCreator
  public PayloadResult(
      @com.fasterxml.jackson.annotation.JsonProperty("payload") JsonNode payload,
      @com.fasterxml.jackson.annotation.JsonProperty("status") String status,
      @com.fasterxml.jackson.annotation.JsonProperty("reason") String reason) {

    this.payload = payload;
    this.status = status;
    this.reason = reason;
  }

  public JsonNode payload() {
    return payload;
  }

  public JsonNode getPayload() {
    return payload;
  }

  public String status() {
    return status;
  }

  public String getStatus() {
    return status;
  }

  public String reason() {
    return reason;
  }

  public String getReason() {
    return reason;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) return true;
    if (other == null || getClass() != other.getClass()) return false;
    PayloadResult that = (PayloadResult) other;
    return java.util.Objects.equals(payload, that.payload)
        && java.util.Objects.equals(status, that.status)
        && java.util.Objects.equals(reason, that.reason);
  }

  @Override
  public int hashCode() {
    return java.util.Objects.hash(payload, status, reason);
  }
}
