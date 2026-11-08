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
  record Participant(
      @LogMask String name,
      @LogMask(strategy = MaskStrategy.LAST_FOUR) String policy,
      @LogExclude String token,
      @LogValue String product) {}

  record Unknown(@LogValue String value) {}

  @LogPayload
  record Request(
      @LogValue List<Participant> participants,
      @LogValue Object unknown,
      @LogValue String optional) {}

  @Test
  void recordsNestedCollectionsNullAndHttpSerializationStayUnchanged() throws Exception {
    var dto =
        new Request(
            List.of(new Participant("Alice", "POL123456", "secret", "health")),
            new Unknown("unclassified"),
            null);
    var http = new ObjectMapper();
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
    var result = serializer.serialize(new Child());
    String json = result.payload().toString();
    assertThat(result.payload().has("token")).isFalse();
    assertThat(json).doesNotContain("secret", "123456", "1234");
  }

  @LogPayload
  static class Cycle {
    @LogValue public Cycle next = this;
    @LogValue public String large = "x".repeat(200);
  }

  @Test
  void cycleAndLimitsProduceValidBoundedJson() throws Exception {
    var result = serializer.serialize(new Cycle());
    assertThat(result.status()).isEqualTo("TRUNCATED");
    assertThat(new ObjectMapper().readTree(result.payload().toString())).isNotNull();
    var tiny = new PayloadLogSerializer(policies, 3, 5, 100, 64).serialize(new Cycle());
    assertThat(tiny.payload().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length)
        .isLessThanOrEqualTo(64);
  }

  @Test
  void externalTypesAndMapsRequireExplicitPolicies() {
    var raw = Map.of("token", "secret", "product", "health");
    assertThat(serializer.serialize(raw).status()).isEqualTo("SKIPPED");
    policies.register(
        Map.class, Map.of("token", MaskStrategy.EXCLUDE, "product", MaskStrategy.NONE));
    assertThat(serializer.serialize(raw).payload().path("product").asText()).isEqualTo("health");
    assertThat(serializer.serialize(raw).payload().has("token")).isFalse();
    assertThat(serializer.serialize("raw secret body").payload().isNull()).isTrue();
    assertThat(serializer.serialize(List.of("raw secret body")).payload().isNull()).isTrue();
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
    var result = serializer.serialize(new Participant("Alice", "1234", "token", null));
    assertThat(result.payload().path("name").asText()).isEqualTo("***");
    assertThat(result.payload().path("policy").asText()).isEqualTo("***");
  }
}
