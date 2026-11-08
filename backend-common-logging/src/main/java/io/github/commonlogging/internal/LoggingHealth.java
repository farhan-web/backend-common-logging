package io.github.commonlogging.internal;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.util.concurrent.atomic.AtomicLong;

/** Bounded counters; never labels a metric with request IDs or payload values. */
public final class LoggingHealth implements MeterBinder {
  private final AtomicLong backendErrors = new AtomicLong();

  public void backendError() {
    backendErrors.incrementAndGet();
  }

  public long backendErrors() {
    return backendErrors.get();
  }

  private final AtomicLong written = new AtomicLong(),
      dropped = new AtomicLong(),
      payloadFailures = new AtomicLong();

  public void written() {
    written.incrementAndGet();
  }

  public void dropped() {
    dropped.incrementAndGet();
  }

  public void payloadFailure() {
    payloadFailures.incrementAndGet();
  }

  public long droppedEvents() {
    return dropped.get();
  }

  public long payloadFailures() {
    return payloadFailures.get();
  }

  @Override
  public void bindTo(MeterRegistry registry) {
    FunctionCounter.builder("common.logging.backend.errors", backendErrors, AtomicLong::doubleValue)
        .register(registry);
    FunctionCounter.builder("common.logging.events.written", written, AtomicLong::doubleValue)
        .register(registry);
    FunctionCounter.builder("common.logging.events.dropped", dropped, AtomicLong::doubleValue)
        .register(registry);
    FunctionCounter.builder(
            "common.logging.payload.failures", payloadFailures, AtomicLong::doubleValue)
        .register(registry);
  }
}
