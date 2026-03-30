package com.maple.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for credit card payment analytics.
 *
 * <p>Contains aggregated metrics from Stripe PaymentIntents. No raw card data or PII included.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Credit card payment analytics (aggregated metrics from Stripe)")
public class CardAnalyticsDto {

    @JsonProperty("periodStart")
    @Schema(description = "Start of the analytics period", example = "2024-01-01T00:00:00Z")
    private OffsetDateTime periodStart;

    @JsonProperty("periodEnd")
    @Schema(description = "End of the analytics period", example = "2024-01-31T23:59:59Z")
    private OffsetDateTime periodEnd;

    @JsonProperty("totalCount")
    @Schema(description = "Total number of card payment attempts in the period")
    private long totalCount;

    @JsonProperty("succeededCount")
    @Schema(description = "Number of successfully completed card payments")
    private long succeededCount;

    @JsonProperty("failedCount")
    @Schema(description = "Number of failed card payment attempts")
    private long failedCount;

    @JsonProperty("totalVolumeCents")
    @Schema(description = "Total volume of succeeded payments in cents")
    private long totalVolumeCents;

    @JsonProperty("averageAmountCents")
    @Schema(description = "Average payment amount in cents (succeeded only)")
    private double averageAmountCents;

    @JsonProperty("successRatePercent")
    @Schema(description = "Success rate as percentage (0-100)")
    private double successRatePercent;

    @JsonProperty("countByStatus")
    @Schema(description = "Payment count broken down by Stripe status (e.g. succeeded, requires_payment_method)")
    private Map<String, Long> countByStatus;

    @JsonProperty("volumeByCurrency")
    @Schema(description = "Volume in cents by currency for succeeded payments")
    private Map<String, Long> volumeByCurrency;
}
