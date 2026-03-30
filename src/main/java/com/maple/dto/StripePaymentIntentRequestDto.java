package com.maple.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Request DTO for creating a Stripe PaymentIntent.
 *
 * <p>Amount is in the smallest currency unit (e.g., cents for USD).
 * Use Stripe's client-side flow for card data — never send raw card details to this API.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to create a Stripe PaymentIntent for card payments")
public class StripePaymentIntentRequestDto {

    @Schema(description = "Amount in smallest currency unit (e.g., cents)", example = "150000", required = true)
    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    @Max(value = 99999999L, message = "Amount exceeds Stripe's maximum per transaction")
    @JsonProperty("amountCents")
    private Long amountCents;

    @Schema(description = "ISO 4217 currency code (lowercase)", example = "usd", required = true)
    @NotBlank(message = "Currency is required")
    @Pattern(regexp = "^[a-z]{3}$", message = "Currency must be a valid 3-letter ISO code (lowercase)")
    @JsonProperty("currency")
    private String currency;

    @Schema(description = "Idempotency key for duplicate prevention")
    @Size(max = 128)
    @JsonProperty("idempotencyKey")
    private String idempotencyKey;

    @Schema(description = "Customer email for receipt")
    @Email
    @Size(max = 255)
    @JsonProperty("receiptEmail")
    private String receiptEmail;

    @Schema(description = "Payment reference for internal tracking")
    @Size(max = 64)
    @Pattern(regexp = "^[A-Za-z0-9_-]*$", message = "Payment reference contains invalid characters")
    @JsonProperty("paymentReference")
    private String paymentReference;

    @Schema(description = "Additional metadata (key-value pairs)")
    @JsonProperty("metadata")
    private Map<String, String> metadata;

    @Schema(description = "Whether to capture immediately (true) or authorize only (false)")
    @JsonProperty("captureMethod")
    private Boolean captureMethod; // true = automatic, false = manual
}
