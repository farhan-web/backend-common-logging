package com.example.insurance.claim;

import io.github.commonlogging.annotation.*;
import io.github.commonlogging.payload.MaskStrategy;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;

@LogPayload
public record ClaimRequest(
    @NotBlank @Size(max = 40) @LogMask(strategy = MaskStrategy.LAST_FOUR) String policyNumber,
    @LogValue @NotNull @DecimalMin("0.01") @Digits(integer = 12, fraction = 2) BigDecimal amount,
    @NotBlank @Email @Size(max = 254) @LogMask String claimantEmail,
    @NotBlank @Size(max = 1000) @LogExclude String description,
    @LogValue @NotBlank @Pattern(regexp = "application/pdf|text/plain") String contentType,
    @NotBlank @Size(max = 1400000) @LogExclude String documentBase64) {}
