package com.example.insurance;

import static org.assertj.core.api.Assertions.*;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import java.net.URI;
import java.net.http.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

@AutoConfigureObservability
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "insurance.demo.port=0",
      "spring.h2.console.enabled=false",
      "spring.datasource.url=jdbc:h2:mem:real-servlet-tracing;DB_CLOSE_DELAY=-1"
    })
@Import(ClaimFlowIntegrationTest.TracingCapture.class)
class TraceServerIntegrationTest {
  @LocalServerPort int port;
  @Autowired InMemorySpanExporter exporter;

  @Test
  void actualServletServerReusesOneFrameworkSpanAndExportsTheResponseSpanId() throws Exception {
    exporter.reset();
    String trace = "4bf92f3577b34da6a3ce929d0e0e4736", upstream = "00f067aa0ba902b7";
    {
      var client = HttpClient.newHttpClient();
      var response =
          client.send(
              HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/policies"))
                  .header("traceparent", "00-" + trace + "-" + upstream + "-01")
                  .GET()
                  .build(),
              HttpResponse.BodyHandlers.ofString());
      assertThat(response.statusCode()).isEqualTo(200);
      assertThat(response.headers().firstValue("X-Trace-ID")).contains(trace);
      org.awaitility.Awaitility.await()
          .atMost(java.time.Duration.ofSeconds(5))
          .untilAsserted(
              () ->
                  assertThat(
                          exporter.getFinishedSpanItems().stream()
                              .anyMatch(
                                  span ->
                                      span.getTraceId().equals(trace)
                                          && span.getKind() == SpanKind.SERVER))
                      .isTrue());
      var spans =
          exporter.getFinishedSpanItems().stream()
              .filter(s -> s.getTraceId().equals(trace))
              .toList();
      var servers = spans.stream().filter(s -> s.getKind() == SpanKind.SERVER).toList();
      assertThat(servers).hasSize(1);
      assertThat(servers.get(0).getParentSpanId()).isEqualTo(upstream);
      assertThat(response.headers().firstValue("X-Span-ID")).contains(servers.get(0).getSpanId());
      assertThat(spans.stream().filter(s -> s.getName().equals("policy.list")))
          .hasSize(1)
          .allSatisfy(s -> assertThat(s.getParentSpanId()).isEqualTo(servers.get(0).getSpanId()));
    }
  }
}
