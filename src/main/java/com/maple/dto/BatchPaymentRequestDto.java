package com.maple.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * Data Transfer Object for batch payment operations.
 * 
 * Used for batch approval, rejection, or other bulk operations on multiple payments.
 */
@Data
@Schema(description = "Request for batch payment operations")
public class BatchPaymentRequestDto {

    @Schema(description = "List of payment IDs to process", required = true, example = "[\"550e8400-e29b-41d4-a716-446655440000\", \"660e8400-e29b-41d4-a716-446655440001\"]")
    @NotNull(message = "Payment IDs list is required")
    @NotEmpty(message = "Payment IDs list cannot be empty")
    @JsonProperty("paymentIds")
    private List<String> paymentIds;

    @Schema(description = "Optional note or reason for the batch operation")
    @JsonProperty("note")
    private String note;

    @Schema(description = "Whether two-factor authentication was verified (for approvals)")
    @JsonProperty("twoFactorVerified")
    private Boolean twoFactorVerified = false;

    @Schema(description = "Rejection reason (required for batch rejection)")
    @JsonProperty("rejectionReason")
    private String rejectionReason;

    @Schema(description = "Approval note (optional for batch approval)")
    @JsonProperty("approvalNote")
    private String approvalNote;
}

