package io.github.commonlogging.annotation;

import java.lang.annotation.*;

/** Explicit approval for a safe field, parameter, or scalar result to enter logs. */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
public @interface LogValue {
  String value() default "";
}
