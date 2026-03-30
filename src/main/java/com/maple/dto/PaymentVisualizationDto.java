package com.maple.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.maple.model.PaymentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.Map;

/**
 * DTO for payment data visualization (charts, dashboards).
 * Returns aggregated, chart-ready data for the authenticated user's payments.
 * No sensitive account numbers or raw payment details are included.
 */
@Value
@Builder
@AllArgsConstructor
@Schema(description = "Chart-ready payment visualization data for the authenticated user")
public class PaymentVisualizationDto {

    @Schema(description = "Volume over time as daily buckets: [{date, volumeCents}, ...]")
    @JsonProperty("volumeOverTime")
    List<VolumeAtDateDto> volumeOverTime;

    @Schema(description = "Payment count by status")
    @JsonProperty("statusBreakdown")
    Map<PaymentStatus, Long> statusBreakdown;

    @Schema(description = "Total volume by currency (cents)")
    @JsonProperty("volumeByCurrency")
    Map<String, Long> volumeByCurrency;

    @Schema(description = "Summary: total payment count in period")
    @JsonProperty("totalCount")
    Long totalCount;

    @Schema(description = "Summary: total volume in period (cents, excluding failed/rejected/cancelled)")
    @JsonProperty("totalVolumeCents")
    Long totalVolumeCents;

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
