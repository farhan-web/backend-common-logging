package com.example.insurance.policy;

import io.github.commonlogging.annotation.*;
import io.github.commonlogging.payload.MaskStrategy;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;

@LogPayload
public record PolicyRequest(
    @NotBlank
        @Size(max = 40)
        @Pattern(regexp = "[A-Za-z0-9-]+")
        @LogMask(strategy = MaskStrategy.LAST_FOUR)
        String policyNumber,
    @NotBlank @Size(max = 120) @LogMask String holderName,
    @NotBlank @Email @Size(max = 254) @LogMask String holderEmail,
    @LogValue @NotNull @DecimalMin("0.01") @Digits(integer = 12, fraction = 2) BigDecimal premium,
    @LogValue @NotNull PolicyStatus status) {}
