package com.maple.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.maple.model.PaymentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * DTO for payment data analytics.
 *
 * <p>Returns aggregated metrics for dashboards and reporting.
 * No sensitive account numbers or raw payment details included.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Aggregated payment analytics for a given period")
public class PaymentAnalyticsDto {

    @JsonProperty("periodStart")
    @Schema(description = "Start of the analytics period")
    private OffsetDateTime periodStart;

    @JsonProperty("periodEnd")
    @Schema(description = "End of the analytics period")
    private OffsetDateTime periodEnd;

    @JsonProperty("totalCount")
    @Schema(description = "Total number of payments in the period")
    private long totalCount;

    @JsonProperty("totalVolumeCents")
    @Schema(description = "Total volume in cents (excluding failed/rejected/cancelled)")
    private long totalVolumeCents;

    @JsonProperty("averageAmountCents")
    @Schema(description = "Average payment amount in cents")
    private double averageAmountCents;

    @JsonProperty("successRatePercent")
    @Schema(description = "Success rate as percentage (0-100)")
    private double successRatePercent;

    @JsonProperty("statusBreakdown")
    @Schema(description = "Payment count by status")
    private Map<PaymentStatus, Long> statusBreakdown;
}
