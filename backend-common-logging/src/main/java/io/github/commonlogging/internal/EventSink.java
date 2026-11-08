package io.github.commonlogging.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;

@FunctionalInterface
public interface EventSink {
  void write(String level, String json);

  default void writeEvent(String level, Map<String, Object> event, ObjectMapper mapper)
      throws Exception {
    write(level, mapper.writeValueAsString(event));
  }
}
