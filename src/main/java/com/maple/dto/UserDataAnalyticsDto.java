package com.maple.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * DTO for aggregated user-activity analytics.
 *
 * <p>Contains only non-identifying metrics (counts). No user IDs, emails, or
 * other PII are included — suitable for auditor/treasury dashboards.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Aggregated user-activity analytics for a period (no PII)")
public class UserDataAnalyticsDto {

    @JsonProperty("periodStart")
    @Schema(description = "Start of the analytics period")
    private OffsetDateTime periodStart;

    @JsonProperty("periodEnd")
    @Schema(description = "End of the analytics period")
    private OffsetDateTime periodEnd;

    @JsonProperty("distinctInitiatorsCount")
    @Schema(description = "Number of distinct users who initiated at least one payment in the period")
    private long distinctInitiatorsCount;

    @JsonProperty("totalPaymentsCreatedInPeriod")
    @Schema(description = "Total payments created in the period (all statuses)")
    private long totalPaymentsCreatedInPeriod;

    @JsonProperty("averagePaymentsPerInitiator")
    @Schema(
            description =
                    "Average number of payments per distinct initiator in the period (0 if no initiators)")
    private double averagePaymentsPerInitiator;
}
