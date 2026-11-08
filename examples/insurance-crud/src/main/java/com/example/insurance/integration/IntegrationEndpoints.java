package com.example.insurance.integration;

import java.net.URI;

public record IntegrationEndpoints(URI core, URI notification, URI s3) {}
