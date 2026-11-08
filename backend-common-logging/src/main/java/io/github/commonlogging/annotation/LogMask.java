package io.github.commonlogging.annotation;

import io.github.commonlogging.payload.MaskStrategy;
import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target({
  ElementType.FIELD,
  ElementType.METHOD,
  ElementType.RECORD_COMPONENT,
  ElementType.PARAMETER
})
public @interface LogMask {
  MaskStrategy strategy() default MaskStrategy.FULL;
}
