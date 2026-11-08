package com.example.insurance.integration;

import java.net.URI;
import java.net.http.HttpClient;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

final class HttpClients {
  private HttpClients() {}

  static RestClient build(RestClient.Builder builder, URI base, IntegrationProperties p) {
    var factory =
        new JdkClientHttpRequestFactory(
            HttpClient.newBuilder().connectTimeout(p.connectTimeout()).build());
    factory.setReadTimeout(p.readTimeout());
    return builder.clone().baseUrl(base.toString()).requestFactory(factory).build();
  }
}
