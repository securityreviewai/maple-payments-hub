package com.maple.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.UUID;

/**
 * Data Transfer Object for batch payment operation responses.
 * 
 * Contains results of batch operations including success/failure counts
 * and individual operation results.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response from batch payment operations")
public class BatchPaymentResponseDto {

    @Schema(description = "Total number of payments processed")
    @JsonProperty("totalProcessed")
    private Integer totalProcessed;

    @Schema(description = "Number of successfully processed payments")
    @JsonProperty("successCount")
    private Long successCount;

    @Schema(description = "Number of failed operations")
    @JsonProperty("failureCount")
    private Long failureCount;

    @Schema(description = "Detailed results for each payment ID")
    @JsonProperty("results")
    private Map<UUID, BatchOperationResultDto> results;

    @Schema(description = "Summary message")
    @JsonProperty("message")
    private String message;

    /**
     * Inner class representing result of a single operation in batch.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Result of a single batch operation")
    public static class BatchOperationResultDto {
        @Schema(description = "Whether the operation was successful")
        @JsonProperty("success")
        private Boolean success;

        @Schema(description = "Result message")
        @JsonProperty("message")
        private String message;

        @Schema(description = "Error details if operation failed")
        @JsonProperty("errorDetails")
        private String errorDetails;
    }
}

