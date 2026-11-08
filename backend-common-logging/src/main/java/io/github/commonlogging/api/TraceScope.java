package io.github.commonlogging.api;

/** Request/job scope; close on the same thread. */
@FunctionalInterface
public interface TraceScope extends AutoCloseable {
  @Override
  void close();
}
