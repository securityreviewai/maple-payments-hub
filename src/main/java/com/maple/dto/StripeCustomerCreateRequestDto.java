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
 * Request DTO for creating a Stripe Customer (user) for payment and subscription use.
 *
 * <p>Used for automatic user creation in Stripe so that PaymentIntents or subscriptions
 * can be associated with a customer record.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to create a Stripe Customer for payments and subscriptions")
public class StripeCustomerCreateRequestDto {

    @Schema(description = "Customer email address", example = "user@example.com", required = true)
    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    @Size(max = 255)
    @JsonProperty("email")
    private String email;

    @Schema(description = "Customer full name")
    @Size(max = 500)
    @JsonProperty("name")
    private String name;

    @Schema(description = "Arbitrary description for the customer (e.g. internal reference)")
    @Size(max = 500)
    @JsonProperty("description")
    private String description;

    @Schema(description = "Idempotency key for duplicate prevention")
    @Size(max = 128)
    @JsonProperty("idempotencyKey")
    private String idempotencyKey;

    @Schema(description = "Additional metadata (key-value pairs). Stripe limits keys to 40 chars, values to 500.")
    @JsonProperty("metadata")
    private Map<String, String> metadata;
}
