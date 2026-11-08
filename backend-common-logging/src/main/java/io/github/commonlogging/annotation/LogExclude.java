package io.github.commonlogging.annotation;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target({
  ElementType.FIELD,
  ElementType.METHOD,
  ElementType.RECORD_COMPONENT,
  ElementType.PARAMETER
})
public @interface LogExclude {}
