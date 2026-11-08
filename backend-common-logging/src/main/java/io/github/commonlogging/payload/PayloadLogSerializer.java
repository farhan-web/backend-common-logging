package io.github.commonlogging.payload;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.introspect.*;
import com.fasterxml.jackson.databind.node.*;
import io.github.commonlogging.annotation.*;
import java.lang.reflect.*;
import java.util.*;

/** Builds a bounded safe tree; never delegates arbitrary object serialization. */
public final class PayloadLogSerializer {
  private final ObjectMapper mapper = new ObjectMapper(); // never the HTTP mapper
  private final PayloadPolicies policies;
  private final int maxDepth, maxItems, maxString, maxBytes, maxNodes;

  public PayloadLogSerializer(
      PayloadPolicies policies, int maxDepth, int maxItems, int maxString, int maxBytes) {
    if (maxDepth < 1 || maxItems < 1 || maxString < 1 || maxBytes < 64)
      throw new IllegalArgumentException("Invalid payload limits");
    this.policies = Objects.requireNonNull(policies);
    mapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
    this.maxDepth = maxDepth;
    this.maxItems = maxItems;
    this.maxString = maxString;
    this.maxBytes = maxBytes;
    this.maxNodes = Math.min(10000, maxBytes);
  }

  public PayloadResult serialize(Object value) {
    State s = new State();
    try {
      if (!rootSupported(value, 0, new IdentityHashMap<>()))
        return new PayloadResult(NullNode.instance, "SKIPPED", "UNSUPPORTED_ROOT_TYPE");
      JsonNode result = visit(value, 0, s);
      if (mapper.writeValueAsBytes(result).length > maxBytes) {
        return new PayloadResult(TextNode.valueOf("[SIZE_LIMIT]"), "TRUNCATED", "SIZE_LIMIT");
      }
      return new PayloadResult(
          result, s.truncated ? "TRUNCATED" : s.skipped ? "SKIPPED" : "CAPTURED", s.reason);
    } catch (Exception ex) {
      return new PayloadResult(NullNode.instance, "FAILED", "SERIALIZATION_FAILED");
    }
  }

  /** Safe preflight for selecting DTO arguments; never serializes or calls DTO getters. */
  public boolean canCapture(Object value) {
    try {
      return rootSupported(value, 0, new IdentityHashMap<>());
    } catch (RuntimeException ignored) {
      return false;
    }
  }

  private boolean rootSupported(Object value, int depth, IdentityHashMap<Object, Boolean> seen) {
    if (value == null) return true;
    if (depth > maxDepth || seen.size() >= maxNodes) return false;
    if (value.getClass().isAnnotationPresent(LogPayload.class)
        || policies.forType(value.getClass()) != null) return true;
    if (seen.put(value, Boolean.TRUE) != null) return false;
    if (value instanceof Collection<?>) {
      Collection<?> c = (Collection<?>) value;
      int n = 0;
      for (Object item : c) {
        if (n++ >= maxItems) break;
        if (!rootSupported(item, depth + 1, seen)) return false;
      }
      return true;
    }
    if (value.getClass().isArray() && !value.getClass().getComponentType().isPrimitive()) {
      for (int i = 0; i < Math.min(Array.getLength(value), maxItems); i++)
        if (!rootSupported(Array.get(value, i), depth + 1, seen)) return false;
      return true;
    }
    return false;
  }

  private JsonNode visit(Object value, int depth, State s) throws Exception {
    if (value == null) return NullNode.instance;
    if (++s.nodes > maxNodes || depth > maxDepth) return limit(s, "DEPTH_OR_NODE_LIMIT");
    if (value instanceof String) return text((String) value, s);
    if (value instanceof Enum<?>) return text(((Enum<?>) value).name(), s);
    if (value instanceof Character || value instanceof UUID) return text(String.valueOf(value), s);
    if (value.getClass().getPackage().getName().equals("java.time")
        && value instanceof java.time.temporal.TemporalAccessor) return text(value.toString(), s);
    if (value instanceof Boolean) return BooleanNode.valueOf((Boolean) value);
    if (value instanceof Number
        && io.github.commonlogging.internal.Java8Collections.set(
                Byte.class,
                Short.class,
                Integer.class,
                Long.class,
                Float.class,
                Double.class,
                java.math.BigInteger.class,
                java.math.BigDecimal.class)
            .contains(value.getClass())) {
      String number = value.toString();
      if (number.length() > maxString) return limit(s, "NUMBER_LIMIT");
      return mapper.readTree(number);
    }
    if (s.seen.put(value, Boolean.TRUE) != null) return limit(s, "CYCLE");
    try {
      if (value instanceof byte[]
          || value instanceof java.io.InputStream
          || value instanceof java.io.File) return skip(s, "BINARY_OR_STREAM");
      if (value instanceof Collection<?>) {
        Collection<?> collection = (Collection<?>) value;
        ArrayNode array = mapper.createArrayNode();
        int n = 0;
        for (Object item : collection) {
          if (n++ >= maxItems || s.nodes >= maxNodes) {
            array.add(limit(s, "ITEM_LIMIT"));
            break;
          }
          array.add(visit(item, depth + 1, s));
        }
        return array;
      }
      if (value.getClass().isArray()) {
        ArrayNode array = mapper.createArrayNode();
        int size = Array.getLength(value);
        for (int i = 0; i < Math.min(size, maxItems) && s.nodes < maxNodes; i++)
          array.add(visit(Array.get(value, i), depth + 1, s));
        if (size > maxItems || s.nodes >= maxNodes) array.add(limit(s, "ITEM_LIMIT"));
        return array;
      }
      Map<String, MaskStrategy> policy = policies.forType(value.getClass());
      if (value instanceof Map<?, ?>) {
        Map<?, ?> map = (Map<?, ?>) value;
        if (policy == null) return skip(s, "UNREGISTERED_MAP");
        ObjectNode node = mapper.createObjectNode();
        int n = 0;
        for (java.util.Map.Entry<java.lang.String, io.github.commonlogging.payload.MaskStrategy>
            field : policy.entrySet()) {
          if (n++ >= maxItems || s.nodes >= maxNodes) {
            limit(s, "ITEM_LIMIT");
            break;
          }
          if (field.getValue() != MaskStrategy.EXCLUDE && map.containsKey(field.getKey()))
            node.set(field.getKey(), masked(map.get(field.getKey()), field.getValue(), depth, s));
        }
        return node;
      }
      if (!value.getClass().isAnnotationPresent(LogPayload.class) && policy == null)
        return skip(s, "UNSUPPORTED_TYPE");
      ObjectNode node = mapper.createObjectNode();
      int n = 0;
      com.fasterxml.jackson.databind.BeanDescription description =
          mapper.getSerializationConfig().introspect(mapper.constructType(value.getClass()));
      for (BeanPropertyDefinition property : description.findProperties()) {
        if (n++ >= maxItems || s.nodes >= maxNodes) {
          limit(s, "ITEM_LIMIT");
          break;
        }
        String name = property.getName();
        if (policy != null && !policy.containsKey(name)) continue;
        MaskStrategy strategy = policy == null ? MaskStrategy.NONE : policy.get(name);
        boolean approved = policy != null;
        // Check every inherited field/getter; stricter annotation always wins.
        for (Class<?> c : contractTypes(value.getClass())) {
          for (Field f : c.getDeclaredFields())
            if (f.getName().equals(property.getInternalName())) {
              approved |=
                  f.isAnnotationPresent(LogValue.class) || f.isAnnotationPresent(LogMask.class);
              strategy = stricter(strategy, annotations(f));
            }
          for (Method m : c.getDeclaredMethods())
            if (isGetter(m, property.getInternalName())) {
              approved |=
                  m.isAnnotationPresent(LogValue.class) || m.isAnnotationPresent(LogMask.class);
              strategy = stricter(strategy, annotations(m));
            }
        }
        if (!approved || strategy == MaskStrategy.EXCLUDE) continue;
        AnnotatedMember accessor = property.getAccessor();
        if (accessor == null) continue;
        accessor.fixAccess(true);
        Object fieldValue = accessor.getValue(value);
        node.set(name, masked(fieldValue, strategy, depth, s));
      }
      return node;
    } finally {
      s.seen.remove(value);
    }
  }

  private JsonNode masked(Object value, MaskStrategy strategy, int depth, State s)
      throws Exception {
    if (value == null) return NullNode.instance;
    if (strategy == MaskStrategy.FULL) return TextNode.valueOf("***");
    if (strategy == MaskStrategy.LAST_FOUR)
      return TextNode.valueOf(
          value instanceof String && ((String) value).length() > 4
              ? "***" + ((String) value).substring(((String) value).length() - 4)
              : "***");
    return visit(value, depth + 1, s);
  }

  private static Set<Class<?>> contractTypes(Class<?> type) {
    Set<Class<?>> types = new LinkedHashSet<>();
    collectTypes(type, types);
    return types;
  }

  private static void collectTypes(Class<?> type, Set<Class<?>> types) {
    if (type == null || type == Object.class || !types.add(type)) return;
    for (Class<?> parent : type.getInterfaces()) collectTypes(parent, types);
    collectTypes(type.getSuperclass(), types);
  }

  private static boolean isGetter(Method m, String name) {
    String cap = Character.toUpperCase(name.charAt(0)) + name.substring(1);
    return m.getParameterCount() == 0
        && (m.getName().equals(name)
            || m.getName().equals("get" + cap)
            || m.getName().equals("is" + cap));
  }

  private static MaskStrategy annotations(java.lang.reflect.AnnotatedElement element) {
    if (element.isAnnotationPresent(LogExclude.class)) return MaskStrategy.EXCLUDE;
    LogMask mask = element.getAnnotation(LogMask.class);
    return mask == null ? MaskStrategy.NONE : mask.strategy();
  }

  private static MaskStrategy stricter(MaskStrategy a, MaskStrategy b) {
    return a.ordinal() > b.ordinal() ? a : b;
  }

  private JsonNode text(String value, State s) {
    if (value.length() > maxString) {
      s.truncated = true;
      s.reason = "STRING_LIMIT";
      return TextNode.valueOf(value.substring(0, maxString) + "[TRUNCATED]");
    }
    return TextNode.valueOf(value);
  }

  private JsonNode limit(State s, String reason) {
    s.truncated = true;
    s.reason = reason;
    return TextNode.valueOf("[" + reason + "]");
  }

  private JsonNode skip(State s, String reason) {
    s.skipped = true;
    s.reason = reason;
    return NullNode.instance;
  }

  private static final class State {
    int nodes;
    boolean truncated, skipped;
    String reason;
    IdentityHashMap<Object, Boolean> seen = new IdentityHashMap<>();
  }
}
