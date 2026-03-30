package com.maple.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for a Stripe PaymentIntent.
 *
 * <p>The clientSecret is used by the client to complete payment via Stripe.js or mobile SDK.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Stripe PaymentIntent response with client secret for completing payment")
public class StripePaymentIntentResponseDto {

    @Schema(description = "Stripe PaymentIntent ID")
    @JsonProperty("id")
    private String id;

    @Schema(description = "Client secret for confirming payment on the client (Stripe.js)")
    @JsonProperty("clientSecret")
    private String clientSecret;

    @Schema(description = "Amount in smallest currency unit")
    @JsonProperty("amountCents")
    private Long amountCents;

    @Schema(description = "Currency code")
    @JsonProperty("currency")
    private String currency;

    @Schema(description = "Current status of the PaymentIntent")
    @JsonProperty("status")
    private String status;

    @Schema(description = "Payment reference if provided")
    @JsonProperty("paymentReference")
    private String paymentReference;
}
