package com.company.pilot;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.*;
import io.github.commonlogging.annotation.*;
import io.github.commonlogging.api.*;
import io.github.commonlogging.internal.*;
import io.github.commonlogging.payload.*;
import io.github.commonlogging.spring.LoggedOperationAspect;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

class LoggedOperationPayloadTest {
  final List<JsonNode> events = new ArrayList<>();
  final ObjectMapper mapper = new ObjectMapper();
  final PayloadLogSerializer serializer =
      new PayloadLogSerializer(new PayloadPolicies(), 6, 20, 100, 4096);

  Service service(boolean payloadEnabled) {
    io.github.commonlogging.internal.StructuredEventWriter writer =
        new StructuredEventWriter(
            (level, json) -> {
              try {
                events.add(mapper.readTree(json));
              } catch (Exception ex) {
                throw new AssertionError(ex);
              }
            },
            new LogOptions("test", "test", true, payloadEnabled, 16, "com.company"),
            serializer);
    org.springframework.aop.aspectj.annotation.AspectJProxyFactory factory =
        new AspectJProxyFactory(new Service());
    factory.addAspect(new LoggedOperationAspect(new TLog(writer), serializer));
    return factory.getProxy();
  }

  @AfterEach
  void cleanup() {
    assertThat(LogContext.traceId()).isNull();
    assertThat(org.slf4j.MDC.get("spanId")).isNull();
  }

  @LogPayload
  static final class Input {
    private final @LogMask String name;
    private final @LogExclude String token;
    private final @LogValue String product;

    @com.fasterxml.jackson.annotation.JsonCreator
    public Input(
        @com.fasterxml.jackson.annotation.JsonProperty("name") String name,
        @com.fasterxml.jackson.annotation.JsonProperty("token") String token,
        @com.fasterxml.jackson.annotation.JsonProperty("product") String product) {

      this.name = name;
      this.token = token;
      this.product = product;
    }

    public String name() {
      return name;
    }

    public String getName() {
      return name;
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
      Input that = (Input) other;
      return java.util.Objects.equals(name, that.name)
          && java.util.Objects.equals(token, that.token)
          && java.util.Objects.equals(product, that.product);
    }

    @Override
    public int hashCode() {
      return java.util.Objects.hash(name, token, product);
    }
  }

  @LogPayload
  static final class Output {
    private final @LogMask String name;
    private final @LogValue String product;

    @com.fasterxml.jackson.annotation.JsonCreator
    public Output(
        @com.fasterxml.jackson.annotation.JsonProperty("name") String name,
        @com.fasterxml.jackson.annotation.JsonProperty("product") String product) {

      this.name = name;
      this.product = product;
    }

    public String name() {
      return name;
    }

    public String getName() {
      return name;
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
      Output that = (Output) other;
      return java.util.Objects.equals(name, that.name)
          && java.util.Objects.equals(product, that.product);
    }

    @Override
    public int hashCode() {
      return java.util.Objects.hash(name, product);
    }
  }

  Input input() {
    return new Input("Alice", "private-token", "health");
  }

  @Test
  void startAndEndContainMaskedDtoWithSameSpanAndOriginalResultUnchanged() throws Exception {
    Input input = input();
    String before = mapper.writeValueAsString(input);
    Output actual = service(true).echo(input);
    assertThat(actual.name()).isEqualTo("Alice");
    assertThat(mapper.writeValueAsString(input)).isEqualTo(before);
    assertThat(events).hasSize(2);
    assertThat(events.get(0).path("event").asText()).isEqualTo("PROCESS_START");
    assertThat(events.get(0).path("payload").path("name").asText()).isEqualTo("***");
    assertThat(events.get(0).path("payload").has("token")).isFalse();
    assertThat(events.get(1).path("event").asText()).isEqualTo("PROCESS_END");
    assertThat(events.get(1).path("payload").path("product").asText()).isEqualTo("health");
    assertThat(events.get(1).path("spanId")).isEqualTo(events.get(0).path("spanId"));
    assertThat(events)
        .allSatisfy(e -> assertThat(e.path("payloadStatus").asText()).isEqualTo("CAPTURED"));
    assertThat(events.toString()).doesNotContain("Alice", "private-token");
  }

  @Test
  void rawCredentialParameterIsSkippedWhileSecondArgumentDtoIsCaptured() {
    service(true).withCredential("raw-credential", input());
    assertThat(events.get(0).path("payload").path("product").asText()).isEqualTo("health");
    assertThat(events.toString()).doesNotContain("raw-credential", "private-token", "Alice");
  }

  @Test
  void multipleDtoArgumentsAreNamedAndNestedMasksApply() {
    List<Output> result =
        service(true).pair("raw-credential", input(), new Input("Bob", "other-token", "motor"));
    assertThat(result).hasSize(2);
    JsonNode args = events.get(0).path("payload").path("arguments");
    assertThat(args).hasSize(2);
    assertThat(args.get(0).path("name").asText()).isEqualTo("first");
    assertThat(args.get(1).path("name").asText()).isEqualTo("second");
    assertThat(events.get(1).path("payload")).hasSize(2);
    assertThat(events.toString())
        .doesNotContain("Alice", "Bob", "raw-credential", "private-token", "other-token");
  }

  @Test
  void annotationAndGlobalSwitchCanDisablePayloadWithoutDisablingLifecycle() {
    service(true).metadataOnly(input());
    assertThat(events).hasSize(2);
    assertThat(events).allSatisfy(e -> assertThat(e.has("payload")).isFalse());
    events.clear();
    service(false).echo(input());
    assertThat(events).hasSize(2);
    assertThat(events).allSatisfy(e -> assertThat(e.has("payload")).isFalse());
  }

  @Test
  void noArgumentVoidNullAndUnsupportedResultsAreRepresentedHonestly() {
    Service service = service(true);
    service.noArgs();
    assertThat(events.get(0).has("payload")).isFalse();
    assertThat(events.get(1).path("payloadStatus").asText()).isEqualTo("CAPTURED");
    events.clear();
    service.nothing(input());
    assertThat(events.get(0).has("payload")).isTrue();
    assertThat(events.get(1).has("payload")).isFalse();
    events.clear();
    assertThat(service.nullResult(input())).isNull();
    assertThat(events.get(1).path("payload").isNull()).isTrue();
    assertThat(events.get(1).path("payloadStatus").asText()).isEqualTo("CAPTURED");
    events.clear();
    assertThat(service.raw("raw-secret")).isEqualTo("raw-secret");
    assertThat(events)
        .allSatisfy(e -> assertThat(e.path("payloadStatus").asText()).isEqualTo("SKIPPED"));
    assertThat(events.toString()).doesNotContain("raw-secret");
  }

  @Test
  void failurePreservesOriginalExceptionAndHasNoSuccessfulResultEvent() {
    IllegalStateException failure = new IllegalStateException("private-message");
    assertThatThrownBy(() -> service(true).fail(input(), failure)).isSameAs(failure);
    assertThat(events)
        .extracting(e -> e.path("event").asText())
        .containsExactly("PROCESS_START", "PROCESS_FAILED");
    assertThat(events.get(0).path("payloadStatus").asText()).isEqualTo("CAPTURED");
    assertThat(events.get(1).has("payload")).isFalse();
    assertThat(events.toString()).doesNotContain("private-message");
  }

  @Test
  void failingCollectionPreflightDoesNotChangeBusinessExecution() {
    Collection<Object> broken =
        new AbstractCollection<Object>() {
          @Override
          public Iterator<Object> iterator() {
            throw new IllegalStateException("private-iteration-failure");
          }

          @Override
          public int size() {
            return 1;
          }
        };
    assertThat(service(true).ignoreCollection(broken).product()).isEqualTo("health");
    assertThat(events.get(0).path("payloadStatus").asText()).isEqualTo("FAILED");
    assertThat(events.toString()).doesNotContain("private-iteration-failure");
  }

  public static class Service {
    @LoggedOperation("test.echo")
    public Output echo(Input input) {
      return new Output(input.name(), input.product());
    }

    @LoggedOperation("test.secondArgument")
    public Output withCredential(String credential, Input input) {
      return new Output(input.name(), input.product());
    }

    @LoggedOperation("test.multiple")
    public List<Output> pair(String credential, Input first, Input second) {
      return io.github.commonlogging.internal.Java8Collections.list(
          new Output(first.name(), first.product()), new Output(second.name(), second.product()));
    }

    @LoggedOperation(value = "test.metadataOnly", logInput = false, logResult = false)
    public Output metadataOnly(Input input) {
      return new Output(input.name(), input.product());
    }

    @LoggedOperation("test.noArgs")
    public Output noArgs() {
      return new Output("Alice", "health");
    }

    @LoggedOperation("test.void")
    public void nothing(Input input) {}

    @LoggedOperation("test.null")
    public Output nullResult(Input input) {
      return null;
    }

    @LoggedOperation("test.raw")
    public String raw(String secret) {
      return secret;
    }

    @LoggedOperation("test.fail")
    public Output fail(Input input, RuntimeException error) {
      throw error;
    }

    @LoggedOperation("test.collection")
    public Output ignoreCollection(Collection<?> input) {
      return new Output("Alice", "health");
    }
  }
}
