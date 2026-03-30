package com.maple.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.maple.model.PaymentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Data Transfer Object for payment responses.
 * 
 * Contains payment information returned to API clients with appropriate
 * field masking based on user permissions.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Payment information response")
public class PaymentResponseDto {

    @Schema(description = "Unique payment identifier", example = "550e8400-e29b-41d4-a716-446655440000")
    @JsonProperty("id")
    private UUID id;

    @Schema(description = "Human-readable payment reference", example = "PAY-2024-001234")
    @JsonProperty("paymentReference")
    private String paymentReference;

    @Schema(description = "Payment amount in cents", example = "150000")
    @JsonProperty("amountCents")
    private Long amountCents;

    @Schema(description = "Formatted amount for display", example = "1,500.00 USD")
    @JsonProperty("formattedAmount")
    private String formattedAmount;

    @Schema(description = "ISO 4217 currency code", example = "USD")
    @JsonProperty("currency")
    private String currency;

    @Schema(description = "Debtor account identifier (may be masked)", example = "ACC-****5678")
    @JsonProperty("debtorAccount")
    private String debtorAccount;

    @Schema(description = "Creditor account identifier (may be masked)", example = "ACC-****4321")
    @JsonProperty("creditorAccount")
    private String creditorAccount;

    @Schema(description = "Creditor name", example = "ABC Corporation Ltd")
    @JsonProperty("creditorName")
    private String creditorName;

    @Schema(description = "Purpose of payment", example = "Invoice payment")
    @JsonProperty("paymentPurpose")
    private String paymentPurpose;

    @Schema(description = "Current payment status")
    @JsonProperty("status")
    private PaymentStatus status;

    @Schema(description = "User ID who initiated the payment")
    @JsonProperty("initiatedBy")
    private UUID initiatedBy;

    @Schema(description = "User ID who approved the payment")
    @JsonProperty("approvedBy")
    private UUID approvedBy;

    @Schema(description = "Whether approval is required for this payment")
    @JsonProperty("approvalRequired")
    private Boolean approvalRequired;

    @Schema(description = "Distinct approvers required (1 single control, 2 dual control)")
    @JsonProperty("requiredApprovers")
    private Integer requiredApprovers;

    @Schema(description = "First approver when dual control is in progress")
    @JsonProperty("firstApprovalBy")
    private UUID firstApprovalBy;

    @Schema(description = "When the first approval was recorded (dual control)")
    @JsonProperty("firstApprovalAt")
    private OffsetDateTime firstApprovalAt;

    @Schema(description = "When the next stuck-approval escalation is due")
    @JsonProperty("escalationDueAt")
    private OffsetDateTime escalationDueAt;

    @Schema(description = "Number of escalation events already raised for this payment")
    @JsonProperty("escalationLevel")
    private Integer escalationLevel;

    @Schema(description = "When the payment was submitted to clearing")
    @JsonProperty("submittedAt")
    private OffsetDateTime submittedAt;

    @Schema(description = "When the payment was settled")
    @JsonProperty("settledAt")
    private OffsetDateTime settledAt;

    @Schema(description = "ISO20022 filename if generated")
    @JsonProperty("iso20022Filename")
    private String iso20022Filename;

    @Schema(description = "Batch ID if payment was included in a batch")
    @JsonProperty("batchId")
    private String batchId;

    @Schema(description = "SFTP delivery status for the payment's batch (when batched)")
    @JsonProperty("batchDeliveryStatus")
    private String batchDeliveryStatus;

    @Schema(description = "When partner receipt was recorded for the batch (if applicable)")
    @JsonProperty("batchReceiptReceivedAt")
    private OffsetDateTime batchReceiptReceivedAt;

    @Schema(description = "When the payment was created")
    @JsonProperty("createdAt")
    private OffsetDateTime createdAt;

    @Schema(description = "When the payment was last updated")
    @JsonProperty("updatedAt")
    private OffsetDateTime updatedAt;

    @Schema(description = "Additional metadata")
    @JsonProperty("metadata")
    private Map<String, Object> metadata;

    @Schema(description = "Operation ID for tracking this request")
    @JsonProperty("operationId")
    private String operationId;

    // Helper methods for field masking based on user roles

    /**
     * Creates a masked version of this DTO for users without full access.
     */
    public PaymentResponseDto maskSensitiveFields() {
        return PaymentResponseDto.builder()
                .id(this.id)
                .paymentReference(this.paymentReference)
                .amountCents(this.amountCents)
                .formattedAmount(this.formattedAmount)
                .currency(this.currency)
                .debtorAccount(maskAccount(this.debtorAccount))
                .creditorAccount(maskAccount(this.creditorAccount))
                .creditorName(this.creditorName)
                .paymentPurpose(this.paymentPurpose)
                .status(this.status)
                .initiatedBy(this.initiatedBy)
                .approvedBy(this.approvedBy)
                .approvalRequired(this.approvalRequired)
                .requiredApprovers(this.requiredApprovers)
                .firstApprovalBy(this.firstApprovalBy)
                .firstApprovalAt(this.firstApprovalAt)
                .escalationDueAt(this.escalationDueAt)
                .escalationLevel(this.escalationLevel)
                .submittedAt(this.submittedAt)
                .settledAt(this.settledAt)
                // Hide sensitive operational fields
                .iso20022Filename(null)
                .batchId(null)
                .batchDeliveryStatus(null)
                .batchReceiptReceivedAt(null)
                .createdAt(this.createdAt)
                .updatedAt(this.updatedAt)
                .metadata(null)
                .operationId(this.operationId)
                .build();
    }

    /**
     * Creates a summary version with only essential fields for lists.
     */
    public PaymentResponseDto summarize() {
        return PaymentResponseDto.builder()
                .id(this.id)
                .paymentReference(this.paymentReference)
                .amountCents(this.amountCents)
                .formattedAmount(this.formattedAmount)
                .currency(this.currency)
                .creditorName(this.creditorName)
                .status(this.status)
                .createdAt(this.createdAt)
                .operationId(this.operationId)
                .build();
    }

    /**
     * Masks account number showing only last 4 digits.
     */
    private String maskAccount(String account) {
        if (account == null || account.length() <= 4) {
            return account;
        }
        return "****" + account.substring(account.length() - 4);
    }

    /**
     * @return true if payment is in a terminal state
     */
    public boolean isTerminal() {
        return status != null && status.isTerminal();
    }

    /**
     * @return true if payment can still be cancelled
     */
    public boolean isCancellable() {
        return status != null && status.canBeCancelled();
    }

    /**
     * @return display-friendly status description
     */
    public String getStatusDescription() {
        if (status == null) return "Unknown";
        
        return switch (status) {
            case CREATED -> "Created - Pending validation";
            case PENDING_APPROVAL -> "Awaiting approval";
            case APPROVED -> "Approved - Ready for processing";
            case SUBMITTED -> "Submitted to clearing network";
            case SETTLED -> "Successfully completed";
            case REJECTED -> "Rejected by approver";
            case FAILED -> "Processing failed";
            case CANCELLED -> "Cancelled";
        };
    }

    /**
     * Calculates processing time if payment is complete.
     */
    public Long getProcessingTimeMinutes() {
        if (createdAt == null || settledAt == null) {
            return null;
        }
        return java.time.Duration.between(createdAt, settledAt).toMinutes();
    }
}
