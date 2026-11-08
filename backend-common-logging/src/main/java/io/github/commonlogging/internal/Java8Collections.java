package io.github.commonlogging.internal;

import java.util.*;

/** Small immutable collection factories shared by the Java 8 adapters and examples. */
public final class Java8Collections {
  private Java8Collections() {}

  @SafeVarargs
  public static <T> List<T> list(T... values) {
    return copyList(Arrays.asList(values));
  }

  @SafeVarargs
  public static <T> Set<T> set(T... values) {
    Set<T> result = new LinkedHashSet<T>();
    for (T value : values)
      if (!result.add(Objects.requireNonNull(value)))
        throw new IllegalArgumentException("Duplicate value");
    return Collections.unmodifiableSet(result);
  }

  @SuppressWarnings("unchecked")
  public static <K, V> Map<K, V> map(Object... entries) {
    if (entries.length % 2 != 0) throw new IllegalArgumentException("Expected key/value pairs");
    Map<K, V> result = new LinkedHashMap<K, V>();
    for (int i = 0; i < entries.length; i += 2) {
      K key = (K) Objects.requireNonNull(entries[i]);
      V value = (V) Objects.requireNonNull(entries[i + 1]);
      if (result.containsKey(key)) throw new IllegalArgumentException("Duplicate key");
      result.put(key, value);
    }
    return Collections.unmodifiableMap(result);
  }

  public static <T> List<T> copyList(Collection<? extends T> values) {
    List<T> copy = new ArrayList<T>(values.size());
    for (T value : values) copy.add(Objects.requireNonNull(value));
    return Collections.unmodifiableList(copy);
  }

  public static <K, V> Map<K, V> copyMap(Map<? extends K, ? extends V> values) {
    Map<K, V> copy = new LinkedHashMap<K, V>();
    values.forEach((k, v) -> copy.put(Objects.requireNonNull(k), Objects.requireNonNull(v)));
    return Collections.unmodifiableMap(copy);
  }
}
