package com.example.insurance.integration;

import com.example.insurance.integration.IntegrationDtos.*;
import io.github.commonlogging.spring.DependencyLogging;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;

@Component
public class NotificationClient {
  private final RestTemplate client;
  private final DependencyLogging log;

  public NotificationClient(
      org.springframework.boot.web.client.RestTemplateBuilder builder,
      IntegrationEndpoints endpoints,
      IntegrationProperties p,
      DependencyLogging log) {
    client = HttpClients.build(builder, endpoints.notification(), p);
    this.log = log;
  }

  public void send(NotificationRequest request) {
    log.callHttp(
        "notification",
        "notification.claim.send",
        request,
        () -> {
          try {
            Receipt response = client.postForObject("/notifications", request, Receipt.class);
            if (response == null || !"SENT".equals(response.result()))
              throw new IntegrationException(
                  "NOTIFICATION_INVALID_RESPONSE",
                  IntegrationException.Kind.INVALID_RESPONSE,
                  null);
            return response;
          } catch (RestClientException error) {
            throw HttpIntegrationErrors.translate("NOTIFICATION", error);
          }
        });
  }
}
