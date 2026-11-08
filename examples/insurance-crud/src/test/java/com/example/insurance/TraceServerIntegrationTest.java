package com.example.insurance;

import static org.assertj.core.api.Assertions.*;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import java.net.HttpURLConnection;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

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
  void actualServletServerHasOneServerSpanAndExportsTheResponseSpanId() throws Exception {
    exporter.reset();
    String trace = "4bf92f3577b34da6a3ce929d0e0e4736", upstream = "00f067aa0ba902b7";
    {
      HttpURLConnection response =
          (HttpURLConnection)
              URI.create("http://127.0.0.1:" + port + "/api/policies").toURL().openConnection();
      response.setConnectTimeout(5000);
      response.setReadTimeout(5000);
      response.setRequestProperty("traceparent", "00-" + trace + "-" + upstream + "-01");
      assertThat(response.getResponseCode()).isEqualTo(200);
      assertThat(response.getHeaderField("X-Trace-ID")).isEqualTo(trace);
      long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
      while (System.nanoTime() < deadline
          && exporter.getFinishedSpanItems().stream()
              .noneMatch(
                  span -> span.getTraceId().equals(trace) && span.getKind() == SpanKind.SERVER))
        Thread.sleep(10);
      java.util.List<io.opentelemetry.sdk.trace.data.SpanData> spans =
          exporter.getFinishedSpanItems().stream()
              .filter(s -> s.getTraceId().equals(trace))
              .collect(java.util.stream.Collectors.toList());
      java.util.List<io.opentelemetry.sdk.trace.data.SpanData> servers =
          spans.stream()
              .filter(s -> s.getKind() == SpanKind.SERVER)
              .collect(java.util.stream.Collectors.toList());
      assertThat(servers).hasSize(1);
      assertThat(servers.get(0).getParentSpanId()).isEqualTo(upstream);
      assertThat(response.getHeaderField("X-Span-ID")).isEqualTo(servers.get(0).getSpanId());
      assertThat(spans.stream().filter(s -> s.getName().equals("policy.list")))
          .hasSize(1)
          .allSatisfy(s -> assertThat(s.getParentSpanId()).isEqualTo(servers.get(0).getSpanId()));
      response.disconnect();
    }
  }
}
