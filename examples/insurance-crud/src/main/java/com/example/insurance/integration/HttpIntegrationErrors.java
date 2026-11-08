package com.example.insurance.integration;

import java.net.*;
import java.net.http.HttpTimeoutException;
import java.util.*;
import org.springframework.web.client.*;

final class HttpIntegrationErrors {
  private HttpIntegrationErrors() {}

  static IntegrationException translate(String code, RuntimeException cause) {
    var kind = IntegrationException.Kind.FAILURE;
    if (cause instanceof RestClientResponseException response) {
      kind =
          response.getStatusCode().value() == 422
              ? IntegrationException.Kind.REJECTED
              : IntegrationException.Kind.FAILURE;
    } else if (cause instanceof ResourceAccessException) {
      kind = IntegrationException.Kind.UNAVAILABLE;
      Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
      for (Throwable e = cause; e != null && seen.add(e); e = e.getCause())
        if (e instanceof SocketTimeoutException || e instanceof HttpTimeoutException)
          kind = IntegrationException.Kind.TIMEOUT;
    } else if (cause instanceof RestClientException)
      kind = IntegrationException.Kind.INVALID_RESPONSE;
    return new IntegrationException(code + "_" + kind.name(), kind, cause);
  }
}
