package io.github.commonlogging.api;

@FunctionalInterface
public interface ErrorClassifier {
  ErrorDetails classify(Throwable error);
}
