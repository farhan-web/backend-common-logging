package io.github.commonlogging.api;

/** Safe code only; do not derive it from exception messages or user input. */
public final class ErrorDetails {
  private final String category;
  private final String code;
  private final boolean expected;

  @com.fasterxml.jackson.annotation.JsonCreator
  public ErrorDetails(
      @com.fasterxml.jackson.annotation.JsonProperty("category") String category,
      @com.fasterxml.jackson.annotation.JsonProperty("code") String code,
      @com.fasterxml.jackson.annotation.JsonProperty("expected") boolean expected) {

    if (category == null
        || !category.matches("[A-Z0-9_]{1,64}")
        || code == null
        || !code.matches("[A-Z0-9_]{1,64}"))
      throw new IllegalArgumentException("Invalid error classification");

    this.category = category;
    this.code = code;
    this.expected = expected;
  }

  public String category() {
    return category;
  }

  public String getCategory() {
    return category;
  }

  public String code() {
    return code;
  }

  public String getCode() {
    return code;
  }

  public boolean expected() {
    return expected;
  }

  public boolean getExpected() {
    return expected;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) return true;
    if (other == null || getClass() != other.getClass()) return false;
    ErrorDetails that = (ErrorDetails) other;
    return java.util.Objects.equals(category, that.category)
        && java.util.Objects.equals(code, that.code)
        && java.util.Objects.equals(expected, that.expected);
  }

  @Override
  public int hashCode() {
    return java.util.Objects.hash(category, code, expected);
  }
}
