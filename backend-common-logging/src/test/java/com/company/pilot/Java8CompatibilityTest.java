package com.company.pilot;

import static org.assertj.core.api.Assertions.*;

import io.github.commonlogging.api.TLog;
import io.github.commonlogging.spring.LoggingAutoConfiguration;
import java.io.*;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootVersion;

class Java8CompatibilityTest {
  @Test
  void runtimeAndFrameworkMatchTheCompatibilityTarget() {
    assertThat(System.getProperty("java.specification.version")).isEqualTo("1.8");
    assertThat(SpringBootVersion.getVersion())
        .isEqualTo(System.getProperty("spring-boot.version", "2.4.3"));
  }

  @Test
  void publicLibraryAndTracingSdkLoadJava8Bytecode() throws Exception {
    for (Class<?> type :
        new Class<?>[] {
          TLog.class,
          io.opentelemetry.api.OpenTelemetry.class,
          io.opentelemetry.sdk.OpenTelemetrySdk.class
        }) {
      try (DataInputStream input =
          new DataInputStream(
              type.getResourceAsStream("/" + type.getName().replace('.', '/') + ".class"))) {
        assertThat(input.readInt()).isEqualTo(0xCAFEBABE);
        input.readUnsignedShort();
        assertThat(input.readUnsignedShort()).as(type.getName()).isLessThanOrEqualTo(52);
      }
    }
  }

  @Test
  void autoConfigurationUsesBoot24Factories() throws Exception {
    Properties factories = new Properties();
    try (InputStream input =
        LoggingAutoConfiguration.class.getResourceAsStream("/META-INF/spring.factories")) {
      assertThat(input).isNotNull();
      factories.load(input);
    }
    assertThat(
            factories.getProperty("org.springframework.boot.autoconfigure.EnableAutoConfiguration"))
        .contains(LoggingAutoConfiguration.class.getName());
  }
}
