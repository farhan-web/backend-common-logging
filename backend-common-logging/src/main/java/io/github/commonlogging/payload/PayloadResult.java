package io.github.commonlogging.payload;

import com.fasterxml.jackson.databind.JsonNode;

public record PayloadResult(JsonNode payload, String status, String reason) {}
