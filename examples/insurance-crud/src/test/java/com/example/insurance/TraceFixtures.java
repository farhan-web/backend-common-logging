package com.example.insurance;

import io.github.commonlogging.api.TraceParent;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

final class TraceFixtures {
  static String id(String label) {
    if (TraceParent.validTraceId(label)) return label;
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(label.getBytes(StandardCharsets.UTF_8)))
          .substring(0, 32);
    } catch (Exception e) {
      throw new AssertionError(e);
    }
  }

  static String parent(String label) {
    return id("parent-" + label).substring(0, 16);
  }

  static String header(String label) {
    return "00-" + id(label) + "-" + parent(label) + "-01";
  }
}
