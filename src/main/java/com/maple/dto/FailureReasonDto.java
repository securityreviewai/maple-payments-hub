package com.maple.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;

/**
 * DTO for a payment failure reason with its occurrence count.
 */
@Value
@Builder
@AllArgsConstructor
@Schema(description = "Failure reason with count for failed payments")
public class FailureReasonDto {

    @Schema(description = "Reason code or message for the failure")
    @JsonProperty("reason")
    String reason;

    @Schema(description = "Number of failed payments with this reason")
    @JsonProperty("count")
    Long count;
}
