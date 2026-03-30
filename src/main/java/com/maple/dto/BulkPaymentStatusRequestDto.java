package com.maple.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.UUID;

/**
 * Request DTO for bulk payment status checks.
 *
 * Accepts a list of payment identifiers for which status information
 * should be returned in a single, efficient call.
 */
@Value
@Builder
@Schema(description = "Bulk payment status request")
public class BulkPaymentStatusRequestDto {

    @Schema(description = "List of payment IDs to check")
    @NotEmpty
    @JsonProperty("paymentIds")
    List<UUID> paymentIds;
}

