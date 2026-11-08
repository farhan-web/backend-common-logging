package io.github.commonlogging.internal;

/** Canonical lowercase hex; no dependency on Java 17 HexFormat. */
public final class Java8Bytes {
  private Java8Bytes() {}

  public static String hex(byte[] bytes) {
    char[] digits = "0123456789abcdef".toCharArray();
    char[] result = new char[bytes.length * 2];
    for (int i = 0; i < bytes.length; i++) {
      result[i * 2] = digits[(bytes[i] & 255) >>> 4];
      result[i * 2 + 1] = digits[bytes[i] & 15];
    }
    return new String(result);
  }
}
