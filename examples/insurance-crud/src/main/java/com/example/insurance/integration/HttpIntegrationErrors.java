package com.example.insurance.integration;

import java.net.*;
import java.util.*;
import org.springframework.web.client.*;

final class HttpIntegrationErrors {
  private HttpIntegrationErrors() {}

  static IntegrationException translate(String code, RuntimeException cause) {
    com.example.insurance.integration.IntegrationException.Kind kind =
        IntegrationException.Kind.FAILURE;
    if (cause instanceof RestClientResponseException) {
      RestClientResponseException response = (RestClientResponseException) cause;
      kind =
          response.getRawStatusCode() == 422
              ? IntegrationException.Kind.REJECTED
              : IntegrationException.Kind.FAILURE;
    } else if (cause instanceof ResourceAccessException) {
      kind = IntegrationException.Kind.UNAVAILABLE;
      Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
      for (Throwable e = cause; e != null && seen.add(e); e = e.getCause())
        if (e instanceof SocketTimeoutException) kind = IntegrationException.Kind.TIMEOUT;
    } else if (cause instanceof RestClientException)
      kind = IntegrationException.Kind.INVALID_RESPONSE;
    return new IntegrationException(code + "_" + kind.name(), kind, cause);
  }
}
