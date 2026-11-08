package com.company.pilot;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.*;
import io.github.commonlogging.annotation.LoggedOperation;
import io.github.commonlogging.api.*;
import io.github.commonlogging.internal.*;
import io.github.commonlogging.spring.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.*;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.*;

class TransactionBoundaryTest {
  @Test
  void defaultTransactionAdviceCommitFailureDoesNotProduceSuccessEnd() {
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(LoggingAutoConfiguration.class))
        .withUserConfiguration(Consumer.class)
        .run(
            ctx -> {
              java.util.List<com.fasterxml.jackson.databind.JsonNode> events =
                  ctx.getBean(Capture.class).events;
              try (io.github.commonlogging.api.TraceScope request =
                  ctx.getBean(TLog.class).withTrace("c4610066e1b3611bbd905b61993d0efc")) {
                assertThatThrownBy(() -> ctx.getBean(Service.class).execute())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("commit failed");
              }
              assertThat(events)
                  .extracting(e -> e.path("event").asText())
                  .containsExactly("PROCESS_START", "PROCESS_FAILED");
            });
  }

  @Configuration(proxyBeanMethods = false)
  @EnableAspectJAutoProxy
  @EnableTransactionManagement
  static class Consumer {
    @Bean
    Service service() {
      return new Service();
    }

    @Bean
    Capture capture() {
      return new Capture();
    }

    @Bean
    PlatformTransactionManager transactionManager() {
      return new AbstractPlatformTransactionManager() {
        @Override
        protected Object doGetTransaction() {
          return new Object();
        }

        @Override
        protected void doBegin(Object tx, TransactionDefinition definition) {}

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
          throw new IllegalStateException("commit failed");
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {}
      };
    }
  }

  static class Capture implements EventSink {
    final List<JsonNode> events = new ArrayList<>();
    final ObjectMapper mapper = new ObjectMapper();

    public void write(String l, String json) {
      try {
        events.add(mapper.readTree(json));
      } catch (Exception e) {
        throw new IllegalStateException(e);
      }
    }
  }

  public static class Service {
    @LoggedOperation("test.transaction")
    @Transactional
    public String execute() {
      return "body-completed";
    }
  }
}
