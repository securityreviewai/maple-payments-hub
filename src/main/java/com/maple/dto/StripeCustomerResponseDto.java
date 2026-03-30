package com.maple.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for a created or retrieved Stripe Customer.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Stripe Customer (user) created or retrieved")
public class StripeCustomerResponseDto {

    @Schema(description = "Stripe Customer ID (e.g. cus_...)")
    @JsonProperty("id")
    private String id;

    @Schema(description = "Customer email")
    @JsonProperty("email")
    private String email;

    @Schema(description = "Customer full name")
    @JsonProperty("name")
    private String name;

    @Schema(description = "Description attached to the customer")
    @JsonProperty("description")
    private String description;

    @Schema(description = "Unix timestamp when the customer was created")
    @JsonProperty("created")
    private Long created;
}
