package io.github.commonlogging.spring;

import io.github.commonlogging.annotation.*;
import io.github.commonlogging.annotation.LoggedOperation;
import io.github.commonlogging.api.*;
import io.github.commonlogging.payload.MaskStrategy;
import io.github.commonlogging.payload.PayloadLogSerializer;
import java.lang.reflect.Method;
import java.util.*;
import org.aspectj.lang.*;
import org.aspectj.lang.annotation.*;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.BridgeMethodResolver;
import org.springframework.core.annotation.Order;

@Aspect
@Order(-100)
public final class LoggedOperationAspect {
  private final TLog log;
  private final PayloadLogSerializer payloads;

  public LoggedOperationAspect(TLog log, PayloadLogSerializer payloads) {
    this.log = log;
    this.payloads = payloads;
  }

  @Around("@annotation(annotation)")
  public Object around(ProceedingJoinPoint point, LoggedOperation annotation) throws Throwable {
    Method method =
        BridgeMethodResolver.findBridgedMethod(
            AopUtils.getMostSpecificMethod(
                ((MethodSignature) point.getSignature()).getMethod(),
                AopUtils.getTargetClass(point.getTarget())));
    if (java.util.concurrent.CompletionStage.class.isAssignableFrom(method.getReturnType())
        || java.util.concurrent.Future.class.isAssignableFrom(method.getReturnType())
        || method.getReturnType().getName().startsWith("reactor.")
        || org.springframework.core.annotation.AnnotatedElementUtils.hasAnnotation(
            method, org.springframework.scheduling.annotation.Async.class)) return point.proceed();
    boolean input = log.isPayloadEnabled() && annotation.logInput() && point.getArgs().length > 0;
    Object snapshotSource =
        input
            ? input(method, ((MethodSignature) point.getSignature()).getMethod(), point.getArgs())
            : null;
    try (LogOperation operation =
        log.startAt(
            annotation.value(),
            snapshotSource,
            input,
            new LogLocation(method.getDeclaringClass().getName(), method.getName()))) {
      try {
        Object result = point.proceed();
        var resultContracts = new ArrayList<Method>();
        collectMethods(method.getDeclaringClass(), method, resultContracts, new HashSet<>());
        resultContracts.add(((MethodSignature) point.getSignature()).getMethod());
        boolean excludeResult =
            resultContracts.stream().anyMatch(m -> m.isAnnotationPresent(LogExclude.class));
        MaskStrategy resultMask =
            resultContracts.stream()
                .map(m -> m.getAnnotation(LogMask.class))
                .filter(Objects::nonNull)
                .map(LogMask::strategy)
                .max(Comparator.comparingInt(Enum::ordinal))
                .orElse(MaskStrategy.NONE);
        boolean approveResult =
            resultContracts.stream().anyMatch(m -> m.isAnnotationPresent(LogValue.class));
        if (log.isPayloadEnabled()
            && annotation.logResult()
            && method.getReturnType() != Void.TYPE
            && !excludeResult
            && resultMask != MaskStrategy.EXCLUDE)
          operation.end(
              resultMask != MaskStrategy.NONE
                  ? new ScalarResult(masked(result, resultMask))
                  : approveResult ? new ScalarResult(result) : result);
        else operation.end();
        return result;
      } catch (Exception ex) {
        operation.fail(ex);
        throw ex;
      }
    }
  }

  private Object input(Method method, Method contract, Object[] arguments) {
    List<InputArgument> selected = new ArrayList<>();
    var parameters = method.getParameters();
    boolean explicitlyExcluded = false;
    for (int i = 0; i < arguments.length; i++) {
      Object value = arguments[i];
      var elements = parameterContracts(method, contract, i);
      if (elements.stream().anyMatch(e -> e.isAnnotationPresent(LogExclude.class))) {
        explicitlyExcluded = true;
        continue;
      }
      MaskStrategy mask =
          elements.stream()
              .map(e -> e.getAnnotation(LogMask.class))
              .filter(Objects::nonNull)
              .map(LogMask::strategy)
              .max(Comparator.comparingInt(Enum::ordinal))
              .orElse(MaskStrategy.NONE);
      LogValue approval =
          elements.stream()
              .map(e -> e.getAnnotation(LogValue.class))
              .filter(Objects::nonNull)
              .findFirst()
              .orElse(null);
      if (mask == MaskStrategy.EXCLUDE) {
        explicitlyExcluded = true;
        continue;
      }
      if (mask != MaskStrategy.NONE
          || approval != null
          || (value != null && payloads.canCapture(value))) {
        String name =
            approval != null && !approval.value().isBlank()
                ? approval.value()
                : parameters[i].getName();
        selected.add(
            new InputArgument(name, mask == MaskStrategy.NONE ? value : masked(value, mask)));
      }
    }
    if (selected.size() == 1 && payloads.canCapture(selected.get(0).value()))
      return selected.get(0).value();
    if (!selected.isEmpty()) return new OperationInput(selected);
    // Let the serializer emit SKIPPED for unsupported input, or CAPTURED null for an actual null.
    return explicitlyExcluded
        ? new OperationInput(List.of())
        : arguments.length == 1 ? arguments[0] : arguments;
  }

  private static List<java.lang.reflect.Parameter> parameterContracts(
      Method method, Method contract, int index) {
    List<java.lang.reflect.Parameter> result = new ArrayList<>();
    result.add(method.getParameters()[index]);
    result.add(contract.getParameters()[index]);
    collectContracts(method.getDeclaringClass(), method, index, result, new HashSet<>());
    return result;
  }

  private static void collectContracts(
      Class<?> type,
      Method method,
      int index,
      List<java.lang.reflect.Parameter> result,
      Set<Class<?>> seen) {
    if (type == null || !seen.add(type)) return;
    for (Method candidate : type.getDeclaredMethods()) {
      if (sameContract(candidate, method)) result.add(candidate.getParameters()[index]);
    }
    for (Class<?> parent : type.getInterfaces())
      collectContracts(parent, method, index, result, seen);
    collectContracts(type.getSuperclass(), method, index, result, seen);
  }

  private static void collectMethods(
      Class<?> type, Method method, List<Method> result, Set<Class<?>> seen) {
    if (type == null || !seen.add(type)) return;
    for (Method candidate : type.getDeclaredMethods()) {
      if (sameContract(candidate, method)) result.add(candidate);
    }
    for (Class<?> parent : type.getInterfaces()) collectMethods(parent, method, result, seen);
    collectMethods(type.getSuperclass(), method, result, seen);
  }

  private static boolean sameContract(Method candidate, Method method) {
    if (!candidate.getName().equals(method.getName())
        || candidate.getParameterCount() != method.getParameterCount()) return false;
    return BridgeMethodResolver.findBridgedMethod(
            AopUtils.getMostSpecificMethod(candidate, method.getDeclaringClass()))
        .equals(method);
  }

  private static Object masked(Object value, MaskStrategy strategy) {
    if (value == null) return null;
    return strategy == MaskStrategy.LAST_FOUR && value instanceof String text && text.length() > 4
        ? "***" + text.substring(text.length() - 4)
        : "***";
  }

  @LogPayload
  private record ScalarResult(@LogValue Object value) {}

  @LogPayload
  private record OperationInput(@LogValue List<InputArgument> arguments) {}

  @LogPayload
  private record InputArgument(@LogValue String name, @LogValue Object value) {}
}
