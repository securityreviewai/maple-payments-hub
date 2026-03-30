package com.maple.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.Map;

/**
 * Chart-ready visualization data for user's Venmo-sourced card/transaction data.
 * Aggregated only; no raw card data or PII.
 */
@Value
@Builder
@AllArgsConstructor
@Schema(description = "Credit card (Venmo-sourced) visualization data for the authenticated user")
public class VenmoCardVisualizationDto {

    @JsonProperty("volumeOverTime")
    @Schema(description = "Daily spend (absolute amount) over time: [{date, volumeCents}, ...]")
    List<VolumeAtDateDto> volumeOverTime;

    @JsonProperty("totalSpendCents")
    @Schema(description = "Total spend in period (cents)")
    Long totalSpendCents;

    @JsonProperty("transactionCount")
    @Schema(description = "Number of transactions in period")
    Long transactionCount;

    @JsonProperty("volumeByType")
    @Schema(description = "Spend by entry type (e.g. payment, transfer)")
    Map<String, Long> volumeByType;

    @Value
    @Builder
    @AllArgsConstructor
    @Schema(description = "Volume at a specific date")
    public static class VolumeAtDateDto {
        @JsonProperty("date")
        String date;
        @JsonProperty("volumeCents")
        Long volumeCents;
    }
}
