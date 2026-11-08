package com.example.insurance.policy;

import io.github.commonlogging.annotation.LogPayload;
import io.github.commonlogging.annotation.LogValue;
import java.util.List;

@LogPayload
public record PoliciesResponse(@LogValue List<PolicyResponse> items) {}
