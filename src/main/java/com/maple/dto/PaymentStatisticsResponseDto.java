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
 * Data Transfer Object for payment statistics responses.
 * 
 * Contains aggregated statistics and metrics about payments.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Payment statistics and metrics")
public class PaymentStatisticsResponseDto {

    @Schema(description = "Overall payment counts by status")
    @JsonProperty("overallStatistics")
    private Map<PaymentStatus, Long> overallStatistics;

    @Schema(description = "Total payment volume in cents for the period")
    @JsonProperty("totalVolume")
    private Long totalVolume;

    @Schema(description = "Average payment amount in cents")
    @JsonProperty("averagePaymentAmount")
    private Double averagePaymentAmount;

    @Schema(description = "Payment success rate as percentage (0-100)")
    @JsonProperty("successRate")
    private Double successRate;

    @Schema(description = "Payment approval rate as percentage (0-100)")
    @JsonProperty("approvalRate")
    private Double approvalRate;

    @Schema(description = "Payment counts by status for the period")
    @JsonProperty("statusBreakdown")
    private Map<PaymentStatus, Long> statusBreakdown;

    @Schema(description = "Number of payments pending approval")
    @JsonProperty("pendingApprovalsCount")
    private Long pendingApprovalsCount;

    @Schema(description = "Start of the statistics period")
    @JsonProperty("periodStart")
    private OffsetDateTime periodStart;

    @Schema(description = "End of the statistics period")
    @JsonProperty("periodEnd")
    private OffsetDateTime periodEnd;

    @Schema(description = "Formatted total volume for display")
    @JsonProperty("formattedTotalVolume")
    private String formattedTotalVolume;

    @Schema(description = "Formatted average amount for display")
    @JsonProperty("formattedAverageAmount")
    private String formattedAverageAmount;
}

