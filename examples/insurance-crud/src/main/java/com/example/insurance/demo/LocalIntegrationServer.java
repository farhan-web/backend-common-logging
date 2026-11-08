package com.example.insurance.demo;

import com.example.insurance.integration.IntegrationDtos.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.*;
import io.github.commonlogging.api.*;
import java.io.*;
import java.math.BigDecimal;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;

/**
 * Local demo only: core + notification + minimal S3 PUT/DELETE protocol, NOT a full S3 emulator.
 */
public final class LocalIntegrationServer implements AutoCloseable {
  public enum Fault {
    NONE,
    REJECT,
    UNAVAILABLE,
    TIMEOUT_AFTER_ACCEPT,
    INVALID_RESPONSE,
    ERROR_AFTER_WRITE
  }

  public record FaultRequest(String operation, Fault fault) {}

  private final HttpServer server;
  private final ExecutorService executor = Executors.newFixedThreadPool(8);
  private final java.util.concurrent.atomic.AtomicInteger active =
      new java.util.concurrent.atomic.AtomicInteger();
  private final Object idle = new Object();
  private final ObjectMapper mapper = new ObjectMapper();
  private final TLog log;
  private final TExceptionLog errors;
  private final Map<String, Fault> faults = new ConcurrentHashMap<>();
  private final Map<String, CoreClaimResponse> claims = new ConcurrentHashMap<>();
  private final Map<String, byte[]> objects = new ConcurrentHashMap<>();
  private final Set<String> notifications = ConcurrentHashMap.newKeySet();
  private final Map<String, String> traces = new ConcurrentHashMap<>();
  private final Map<String, Map<String, String>> spans = new ConcurrentHashMap<>();

  public LocalIntegrationServer(int port, TLog log, TExceptionLog errors) {
    this.log = log;
    this.errors = errors;
    try {
      server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
      server.setExecutor(executor);
      server.createContext("/", this::dispatch);
      server.start();
    } catch (IOException ex) {
      executor.shutdownNow();
      throw new IllegalStateException("Could not start local integration simulator", ex);
    }
  }

  public int port() {
    return server.getAddress().getPort();
  }

  public void fault(String operation, Fault fault) {
    if (!Set.of(
            "core.verify", "core.submit", "core.lookup", "s3.put", "s3.delete", "notification.send")
        .contains(operation)) throw new IllegalArgumentException("Unknown simulator operation");
    faults.put(operation, fault);
  }

  public void reset() {
    faults.clear();
    claims.clear();
    objects.clear();
    notifications.clear();
    traces.clear();
    spans.clear();
  }

  public Map<String, Object> state() {
    return Map.of(
        "coreClaims",
        claims.size(),
        "objects",
        objects.size(),
        "notifications",
        notifications.size(),
        "traces",
        Map.copyOf(traces));
  }

  private Fault consume(String key) {
    Fault f = faults.remove(key);
    return f == null ? Fault.NONE : f;
  }

  private void dispatch(HttpExchange exchange) throws IOException {
    active.incrementAndGet();
    String path = exchange.getRequestURI().getPath();
    String method = exchange.getRequestMethod();
    String operation =
        path.startsWith("/s3/")
            ? "s3." + (method.equals("PUT") ? "put" : "delete")
            : path.equals("/core/policies/verify")
                ? "core.verify"
                : path.equals("/core/claims")
                    ? "core.submit"
                    : path.startsWith("/core/claims/")
                        ? "core.lookup"
                        : path.equals("/notifications") ? "notification.send" : "admin";
    try (TraceScope context =
            log.withTraceparent(exchange.getRequestHeaders().getFirst("traceparent"));
        LogOperation scope = log.start("simulator." + operation)) {
      if (!operation.equals("admin")) {
        traces.put(operation, log.traceId());
        Map<String, String> span = new LinkedHashMap<>();
        span.put("traceId", scope.context().traceId());
        span.put("spanId", scope.context().spanId());
        if (scope.context().parentSpanId() != null)
          span.put("parentSpanId", scope.context().parentSpanId());
        spans.put(operation, Map.copyOf(span));
      }
      try {
        if (path.equals("/__demo/state") && method.equals("GET")) {
          json(exchange, 200, state());
        } else if (path.equals("/__demo/reset") && method.equals("POST")) {
          reset();
          json(exchange, 200, new Receipt("RESET"));
        } else if (path.equals("/__demo/faults") && method.equals("POST")) {
          FaultRequest request = read(exchange, FaultRequest.class);
          fault(request.operation(), request.fault());
          json(exchange, 200, new Receipt("CONFIGURED"));
        } else if (path.startsWith("/s3/")) {
          s3(exchange, operation);
        } else if (operation.equals("core.verify") && method.equals("POST")) {
          VerifyRequest request = read(exchange, VerifyRequest.class);
          Fault f = consume(operation);
          if (f == Fault.UNAVAILABLE) {
            json(exchange, 503, new Receipt("UNAVAILABLE"));
          } else if (f == Fault.REJECT) {
            json(exchange, 422, new Receipt("REJECTED"));
          } else if (f == Fault.INVALID_RESPONSE) {
            json(exchange, 200, Map.of("coverageLimit", "not-a-number"));
          } else {
            json(exchange, 200, new VerifyResponse(true, new BigDecimal("100000000.00")));
          }
        } else if (operation.equals("core.submit") && method.equals("POST")) {
          CoreClaimRequest request = read(exchange, CoreClaimRequest.class);
          Fault f = consume(operation);
          if (f == Fault.REJECT) {
            json(exchange, 422, new Receipt("REJECTED"));
          } else if (f == Fault.UNAVAILABLE) {
            json(exchange, 503, new Receipt("UNAVAILABLE"));
          } else {
            CoreClaimResponse result =
                claims.computeIfAbsent(
                    request.externalReference(),
                    id -> new CoreClaimResponse("CORE-" + id, id, "ACCEPTED"));
            if (f == Fault.TIMEOUT_AFTER_ACCEPT) Thread.sleep(1800);
            if (f == Fault.INVALID_RESPONSE) json(exchange, 200, Map.of("unexpected", true));
            else json(exchange, 200, result);
          }
        } else if (operation.equals("core.lookup") && method.equals("GET")) {
          Fault f = consume(operation);
          if (f == Fault.UNAVAILABLE) json(exchange, 503, new Receipt("UNAVAILABLE"));
          else if (f == Fault.REJECT) json(exchange, 404, new Receipt("NOT_VISIBLE"));
          else {
            CoreClaimResponse result = claims.get(path.substring("/core/claims/".length()));
            json(
                exchange,
                result == null ? 404 : 200,
                result == null ? new Receipt("NOT_FOUND") : result);
          }
        } else if (operation.equals("notification.send") && method.equals("POST")) {
          NotificationRequest request = read(exchange, NotificationRequest.class);
          Fault f = consume(operation);
          if (f == Fault.UNAVAILABLE) json(exchange, 503, new Receipt("UNAVAILABLE"));
          else {
            notifications.add(request.claimId());
            json(exchange, 200, new Receipt("SENT"));
          }
        } else json(exchange, 404, new Receipt("NOT_FOUND"));
        scope.end();
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
        scope.fail(interrupted);
      } catch (Exception error) {
        scope.fail(error);
        // Client disconnect after a simulated timeout is expected; never dump raw request/messages.
        if (!(error instanceof IOException)) errors.error(scope.context(), error);
        try {
          json(exchange, 500, new Receipt("SIMULATOR_ERROR"));
        } catch (IOException ignored) {
        }
      }
    } finally {
      exchange.close();
      active.decrementAndGet();
      synchronized (idle) {
        idle.notifyAll();
      }
    }
  }

  private void s3(HttpExchange exchange, String operation) throws Exception {
    String key = exchange.getRequestURI().getPath().substring("/s3/".length());
    Fault fault = consume(operation);
    if (fault == Fault.UNAVAILABLE) {
      s3Error(exchange);
      return;
    }
    if (exchange.getRequestMethod().equals("PUT")) {
      byte[] bytes = exchange.getRequestBody().readNBytes(1048577);
      if (bytes.length > 1048576) {
        s3Error(exchange);
        return;
      }
      objects.put(key, bytes);
      if (fault == Fault.ERROR_AFTER_WRITE) {
        s3Error(exchange);
        return;
      }
      if (fault == Fault.TIMEOUT_AFTER_ACCEPT) Thread.sleep(1800);
      exchange
          .getResponseHeaders()
          .set(
              "ETag",
              "\""
                  + HexFormat.of().formatHex(MessageDigest.getInstance("MD5").digest(bytes))
                  + "\"");
      exchange.sendResponseHeaders(200, -1);
    } else if (exchange.getRequestMethod().equals("DELETE")) {
      objects.remove(key);
      exchange.sendResponseHeaders(204, -1);
    } else {
      exchange.sendResponseHeaders(405, -1);
    }
  }

  private void s3Error(HttpExchange exchange) throws IOException {
    byte[] body =
        "<Error><Code>ServiceUnavailable</Code><Message>Demo failure</Message></Error>"
            .getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "application/xml");
    exchange.sendResponseHeaders(503, body.length);
    exchange.getResponseBody().write(body);
  }

  private <T> T read(HttpExchange exchange, Class<T> type) throws IOException {
    byte[] bytes = exchange.getRequestBody().readNBytes(16385);
    if (bytes.length > 16384) throw new IOException("Demo payload limit");
    return mapper.readValue(bytes, type);
  }

  private void json(HttpExchange exchange, int status, Object body) throws IOException {
    byte[] bytes = mapper.writeValueAsBytes(body);
    exchange.getResponseHeaders().set("Content-Type", "application/json");
    exchange.sendResponseHeaders(status, bytes.length);
    exchange.getResponseBody().write(bytes);
  }

  public byte[] objectContent(String bucket, String key) {
    byte[] value = objects.get(bucket + "/" + key);
    return value == null ? null : value.clone();
  }

  public void awaitIdle() throws InterruptedException {
    long deadline = System.nanoTime() + java.time.Duration.ofSeconds(5).toNanos();
    synchronized (idle) {
      while (active.get() > 0) {
        long left = deadline - System.nanoTime();
        if (left <= 0) throw new IllegalStateException("Simulator requests did not finish");
        idle.wait(Math.max(1, left / 1000000));
      }
    }
  }

  @Override
  public void close() {
    server.stop(0);
    executor.shutdownNow();
  }
}
