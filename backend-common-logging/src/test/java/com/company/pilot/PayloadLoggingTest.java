package com.company.pilot;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.*;
import io.github.commonlogging.annotation.*;
import io.github.commonlogging.payload.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class PayloadLoggingTest {
  final PayloadPolicies policies = new PayloadPolicies();
  final PayloadLogSerializer serializer = new PayloadLogSerializer(policies, 5, 10, 100, 4096);

  @LogPayload
  static final class Participant {
    private final @LogMask String name;
    private final @LogMask(strategy = MaskStrategy.LAST_FOUR) String policy;
    private final @LogExclude String token;
    private final @LogValue String product;

    @com.fasterxml.jackson.annotation.JsonCreator
    public Participant(
        @com.fasterxml.jackson.annotation.JsonProperty("name") String name,
        @com.fasterxml.jackson.annotation.JsonProperty("policy") String policy,
        @com.fasterxml.jackson.annotation.JsonProperty("token") String token,
        @com.fasterxml.jackson.annotation.JsonProperty("product") String product) {

      this.name = name;
      this.policy = policy;
      this.token = token;
      this.product = product;
    }

    public String name() {
      return name;
    }

    public String getName() {
      return name;
    }

    public String policy() {
      return policy;
    }

    public String getPolicy() {
      return policy;
    }

    public String token() {
      return token;
    }

    public String getToken() {
      return token;
    }

    public String product() {
      return product;
    }

    public String getProduct() {
      return product;
    }

    @Override
    public boolean equals(Object other) {
      if (this == other) return true;
      if (other == null || getClass() != other.getClass()) return false;
      Participant that = (Participant) other;
      return java.util.Objects.equals(name, that.name)
          && java.util.Objects.equals(policy, that.policy)
          && java.util.Objects.equals(token, that.token)
          && java.util.Objects.equals(product, that.product);
    }

    @Override
    public int hashCode() {
      return java.util.Objects.hash(name, policy, token, product);
    }
  }

  static final class Unknown {
    private final @LogValue String value;

    @com.fasterxml.jackson.annotation.JsonCreator
    public Unknown(@com.fasterxml.jackson.annotation.JsonProperty("value") String value) {

      this.value = value;
    }

    public String value() {
      return value;
    }

    public String getValue() {
      return value;
    }

    @Override
    public boolean equals(Object other) {
      if (this == other) return true;
      if (other == null || getClass() != other.getClass()) return false;
      Unknown that = (Unknown) other;
      return java.util.Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
      return java.util.Objects.hash(value);
    }
  }

  @LogPayload
  static final class Request {
    private final @LogValue List<Participant> participants;
    private final @LogValue Object unknown;
    private final @LogValue String optional;

    @com.fasterxml.jackson.annotation.JsonCreator
    public Request(
        @com.fasterxml.jackson.annotation.JsonProperty("participants")
            List<Participant> participants,
        @com.fasterxml.jackson.annotation.JsonProperty("unknown") Object unknown,
        @com.fasterxml.jackson.annotation.JsonProperty("optional") String optional) {

      this.participants = participants;
      this.unknown = unknown;
      this.optional = optional;
    }

    public List<Participant> participants() {
      return participants;
    }

    public List<Participant> getParticipants() {
      return participants;
    }

    public Object unknown() {
      return unknown;
    }

    public Object getUnknown() {
      return unknown;
    }

    public String optional() {
      return optional;
    }

    public String getOptional() {
      return optional;
    }

    @Override
    public boolean equals(Object other) {
      if (this == other) return true;
      if (other == null || getClass() != other.getClass()) return false;
      Request that = (Request) other;
      return java.util.Objects.equals(participants, that.participants)
          && java.util.Objects.equals(unknown, that.unknown)
          && java.util.Objects.equals(optional, that.optional);
    }

    @Override
    public int hashCode() {
      return java.util.Objects.hash(participants, unknown, optional);
    }
  }

  @Test
  void recordsNestedCollectionsNullAndHttpSerializationStayUnchanged() throws Exception {
    com.company.pilot.PayloadLoggingTest.Request dto =
        new Request(
            io.github.commonlogging.internal.Java8Collections.list(
                new Participant("Alice", "POL123456", "secret", "health")),
            new Unknown("unclassified"),
            null);
    com.fasterxml.jackson.databind.ObjectMapper http = new ObjectMapper();
    String before = http.writeValueAsString(dto);
    PayloadResult result = serializer.serialize(dto);
    String json = result.payload().toString();
    assertThat(json).contains("***3456", "health").doesNotContain("Alice", "secret", "POL123456");
    assertThat(result.payload().path("participants").get(0).has("token")).isFalse();
    assertThat(result.payload().path("optional").isNull()).isTrue();
    assertThat(http.writeValueAsString(dto))
        .isEqualTo(before)
        .contains("Alice", "secret", "POL123456");
    assertThat(result.status()).isEqualTo("SKIPPED");
  }

  static class Parent {
    @LogExclude
    public String getToken() {
      return "super-secret";
    }
  }

  @LogPayload
  static class Child extends Parent {
    @Override
    public String getToken() {
      return "child-secret";
    }

    @LogMask(strategy = MaskStrategy.LAST_FOUR)
    public int number = 123456;

    @LogMask(strategy = MaskStrategy.LAST_FOUR)
    public String shortId = "1234";
  }

  @Test
  void inheritedExclusionWinsAndUnsupportedPartialMaskDoesNotLeak() {
    io.github.commonlogging.payload.PayloadResult result = serializer.serialize(new Child());
    String json = result.payload().toString();
    assertThat(result.payload().has("token")).isFalse();
    assertThat(json).doesNotContain("secret", "123456", "1234");
  }

  @LogPayload
  static class Cycle {
    @LogValue public Cycle next = this;
    @LogValue public String large = String.join("", java.util.Collections.nCopies(200, "x"));
  }

  @Test
  void cycleAndLimitsProduceValidBoundedJson() throws Exception {
    io.github.commonlogging.payload.PayloadResult result = serializer.serialize(new Cycle());
    assertThat(result.status()).isEqualTo("TRUNCATED");
    assertThat(new ObjectMapper().readTree(result.payload().toString())).isNotNull();
    io.github.commonlogging.payload.PayloadResult tiny =
        new PayloadLogSerializer(policies, 3, 5, 100, 64).serialize(new Cycle());
    assertThat(tiny.payload().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length)
        .isLessThanOrEqualTo(64);
  }

  @Test
  void externalTypesAndMapsRequireExplicitPolicies() {
    java.util.Map<java.lang.String, java.lang.String> raw =
        io.github.commonlogging.internal.Java8Collections.map(
            "token", "secret", "product", "health");
    assertThat(serializer.serialize(raw).status()).isEqualTo("SKIPPED");
    policies.register(
        Map.class,
        io.github.commonlogging.internal.Java8Collections.map(
            "token", MaskStrategy.EXCLUDE, "product", MaskStrategy.NONE));
    assertThat(serializer.serialize(raw).payload().path("product").asText()).isEqualTo("health");
    assertThat(serializer.serialize(raw).payload().has("token")).isFalse();
    assertThat(serializer.serialize("raw secret body").payload().isNull()).isTrue();
    assertThat(
            serializer
                .serialize(
                    io.github.commonlogging.internal.Java8Collections.list("raw secret body"))
                .payload()
                .isNull())
        .isTrue();
  }

  @LogPayload
  static class Exploding {
    @LogValue
    public String getValue() {
      throw new IllegalStateException("secret-in-exception");
    }
  }

  @Test
  void getterFailureHasNoRawFallback() {
    PayloadResult result = serializer.serialize(new Exploding());
    assertThat(result.status()).isEqualTo("FAILED");
    assertThat(result.toString()).doesNotContain("secret-in-exception");
  }

  @Test
  void fullMaskIsDefaultAndShortIdentifiersStayHidden() {
    io.github.commonlogging.payload.PayloadResult result =
        serializer.serialize(new Participant("Alice", "1234", "token", null));
    assertThat(result.payload().path("name").asText()).isEqualTo("***");
    assertThat(result.payload().path("policy").asText()).isEqualTo("***");
  }
}
