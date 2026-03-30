package com.maple.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.maple.model.PaymentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Lightweight DTO for payment status responses.
 *
 * Used by the status endpoint for efficient polling and integration use cases
 * without returning full payment details.
 */
@Value
@Builder
@Schema(description = "Minimal payment status response")
public class PaymentStatusDto {

    @Schema(description = "Unique payment identifier", example = "550e8400-e29b-41d4-a716-446655440000")
    @JsonProperty("id")
    UUID id;

    @Schema(description = "Human-readable payment reference", example = "PAY-2024-001234")
    @JsonProperty("paymentReference")
    String paymentReference;

    @Schema(description = "Current payment status")
    @JsonProperty("status")
    PaymentStatus status;

    @Schema(description = "Whether the payment is in a terminal state (no further transitions)")
    @JsonProperty("isTerminal")
    boolean isTerminal;

    @Schema(description = "When the payment was last updated")
    @JsonProperty("updatedAt")
    OffsetDateTime updatedAt;

    @Schema(description = "When the payment was submitted to clearing (if applicable)")
    @JsonProperty("submittedAt")
    OffsetDateTime submittedAt;

    @Schema(description = "When the payment was settled (if applicable)")
    @JsonProperty("settledAt")
    OffsetDateTime settledAt;
}
