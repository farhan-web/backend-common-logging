package com.example.insurance.integration;

import com.example.insurance.integration.IntegrationDtos.*;
import io.github.commonlogging.spring.DependencyLogging;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;

@Component
public class CoreInsuranceClient {
  private final RestClient client;
  private final DependencyLogging log;

  public CoreInsuranceClient(
      RestClient.Builder builder,
      IntegrationEndpoints endpoints,
      IntegrationProperties properties,
      DependencyLogging log) {
    this.client = HttpClients.build(builder, endpoints.core(), properties);
    this.log = log;
  }

  public VerifyResponse verify(VerifyRequest request) {
    return log.callHttp(
        "core-insurance",
        "core.policy.verify",
        request,
        () -> {
          try {
            VerifyResponse response =
                client
                    .post()
                    .uri("/policies/verify")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(VerifyResponse.class);
            if (response == null
                || response.coverageLimit() == null
                || response.coverageLimit().signum() < 0)
              throw new IntegrationException(
                  "CORE_VERIFY_INVALID_RESPONSE", IntegrationException.Kind.INVALID_RESPONSE, null);
            return response;
          } catch (RestClientException ex) {
            throw HttpIntegrationErrors.translate("CORE_VERIFY", ex);
          }
        });
  }

  public CoreClaimResponse submit(CoreClaimRequest request) {
    return log.callHttp(
        "core-insurance",
        "core.claim.submit",
        request,
        () -> {
          try {
            return validate(
                client
                    .post()
                    .uri("/claims")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(CoreClaimResponse.class),
                request.externalReference());
          } catch (RestClientException ex) {
            throw HttpIntegrationErrors.translate("CORE_SUBMIT", ex);
          }
        });
  }

  public CoreClaimResponse lookup(String externalReference) {
    return log.callHttp(
        "core-insurance",
        "core.claim.lookup",
        new ObjectReference(externalReference),
        () -> {
          try {
            return validate(
                client
                    .get()
                    .uri("/claims/{id}", externalReference)
                    .retrieve()
                    .body(CoreClaimResponse.class),
                externalReference);
          } catch (HttpClientErrorException.NotFound absent) {
            return null;
          } catch (RestClientException ex) {
            throw HttpIntegrationErrors.translate("CORE_LOOKUP", ex);
          }
        });
  }

  private CoreClaimResponse validate(CoreClaimResponse response, String externalReference) {
    if (response == null
        || response.coreClaimId() == null
        || response.coreClaimId().isBlank()
        || !externalReference.equals(response.externalReference())
        || !"ACCEPTED".equals(response.status()))
      throw new IntegrationException(
          "CORE_CLAIM_INVALID_RESPONSE", IntegrationException.Kind.INVALID_RESPONSE, null);
    return response;
  }
}
