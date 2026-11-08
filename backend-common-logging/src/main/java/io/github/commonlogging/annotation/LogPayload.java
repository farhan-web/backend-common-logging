package io.github.commonlogging.annotation;

import java.lang.annotation.*;

/** Opt-in after all DTO fields have been classified for logging. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Inherited
public @interface LogPayload {}
