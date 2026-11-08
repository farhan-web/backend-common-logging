package io.github.commonlogging.payload;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** External types/maps are opt-in through explicit property allowlists. */
public final class PayloadPolicies {
  private final Map<Class<?>, Map<String, MaskStrategy>> policies = new ConcurrentHashMap<>();

  public void register(Class<?> type, Map<String, MaskStrategy> fields) {
    policies.put(
        Objects.requireNonNull(type),
        io.github.commonlogging.internal.Java8Collections.copyMap(fields));
  }

  public Map<String, MaskStrategy> forType(Class<?> type) {
    Map<String, MaskStrategy> exact = policies.get(type);
    if (exact == null && Map.class.isAssignableFrom(type)) return policies.get(Map.class);
    return exact;
  }
}
