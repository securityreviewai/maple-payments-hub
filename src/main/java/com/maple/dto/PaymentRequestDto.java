package com.maple.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.util.Map;

/**
 * Data Transfer Object for payment creation requests.
 * 
 * Contains all the information required to initiate a new payment
 * with validation rules and API documentation.
 */
@Data
@Schema(description = "Request to create a new payment")
public class PaymentRequestDto {

    @Schema(description = "Unique payment reference", example = "PAY-2024-001234", required = true)
    @NotBlank(message = "Payment reference is required")
    @Size(max = 64, message = "Payment reference must not exceed 64 characters")
    @Pattern(regexp = "^[A-Z0-9-]+$", message = "Payment reference must contain only uppercase letters, numbers, and hyphens")
    @JsonProperty("paymentReference")
    private String paymentReference;

    @Schema(description = "Payment amount in cents", example = "150000", required = true)
    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    @Max(value = 99999999999L, message = "Amount exceeds maximum limit")
    @JsonProperty("amountCents")
    private Long amountCents;

    @Schema(description = "ISO 4217 currency code", example = "USD", required = true)
    @NotBlank(message = "Currency is required")
    @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a valid 3-letter ISO code")
    @JsonProperty("currency")
    private String currency = "USD";

    @Schema(description = "Debtor account identifier", example = "ACC-12345678", required = true)
    @NotBlank(message = "Debtor account is required")
    @Size(max = 64, message = "Debtor account must not exceed 64 characters")
    @JsonProperty("debtorAccount")
    private String debtorAccount;

    @Schema(description = "Creditor account identifier", example = "ACC-87654321", required = true)
    @NotBlank(message = "Creditor account is required")
    @Size(max = 64, message = "Creditor account must not exceed 64 characters")
    @JsonProperty("creditorAccount")
    private String creditorAccount;

    @Schema(description = "Creditor name", example = "ABC Corporation Ltd")
    @Size(max = 140, message = "Creditor name must not exceed 140 characters")
    @JsonProperty("creditorName")
    private String creditorName;

    @Schema(description = "Purpose of payment", example = "Invoice payment for services rendered")
    @Size(max = 500, message = "Payment purpose must not exceed 500 characters")
    @JsonProperty("paymentPurpose")
    private String paymentPurpose;

    @Schema(description = "Idempotency key for duplicate prevention", example = "idem-key-12345")
    @Size(max = 128, message = "Idempotency key must not exceed 128 characters")
    @JsonProperty("idempotencyKey")
    private String idempotencyKey;

    @Schema(description = "Additional metadata for the payment")
    @JsonProperty("metadata")
    private Map<String, Object> metadata;

    // Validation methods

    /**
     * Validates that the payment amount is within reasonable business limits.
     */
    @AssertTrue(message = "Payment amount must be at least 1 cent")
    public boolean isValidAmount() {
        return amountCents != null && amountCents >= 1;
    }

    /**
     * Validates that debtor and creditor accounts are different.
     */
    @AssertTrue(message = "Debtor and creditor accounts must be different")
    public boolean isDifferentAccounts() {
        if (debtorAccount == null || creditorAccount == null) {
            return true; // Let @NotBlank handle null validation
        }
        return !debtorAccount.equals(creditorAccount);
    }

    /**
     * Custom validation for account format (can be extended for specific formats).
     */
    public boolean isValidAccountFormat(String account) {
        if (account == null) return false;
        // Basic validation - can be enhanced for specific account number formats
        return account.matches("^[A-Z0-9-]+$") && account.length() >= 4;
    }

    /**
     * @return true if this appears to be a high-value payment requiring special handling
     */
    public boolean isHighValue() {
        return amountCents != null && amountCents > 10000000L; // > $100,000
    }

    /**
     * @return formatted amount for display (amount in dollars)
     */
    public String getFormattedAmount() {
        if (amountCents == null) return "0.00";
        return String.format("%.2f", amountCents / 100.0);
    }
}
