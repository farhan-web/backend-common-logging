package com.example.insurance.integration;

import java.net.URI;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.web.client.RestTemplate;

final class HttpClients {
  private HttpClients() {}

  static RestTemplate build(
      RestTemplateBuilder builder, URI base, IntegrationProperties properties) {
    return builder
        .rootUri(base.toString())
        .setConnectTimeout(properties.connectTimeout())
        .setReadTimeout(properties.readTimeout())
        .build();
  }
}
