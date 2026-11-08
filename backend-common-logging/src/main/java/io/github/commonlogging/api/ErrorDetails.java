package io.github.commonlogging.api;

/** Safe code only; do not derive it from exception messages or user input. */
public record ErrorDetails(String category, String code, boolean expected) {
  public ErrorDetails {
    if (category == null
        || !category.matches("[A-Z0-9_]{1,64}")
        || code == null
        || !code.matches("[A-Z0-9_]{1,64}"))
      throw new IllegalArgumentException("Invalid error classification");
  }
}
