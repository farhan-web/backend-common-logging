package io.github.commonlogging.annotation;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface LoggedOperation {
  String value();

  /** Capture opted-in input DTOs when the global payload switch is enabled. */
  boolean logInput() default true;

  /** Capture the masked result when non-void and globally enabled. */
  boolean logResult() default true;
}
