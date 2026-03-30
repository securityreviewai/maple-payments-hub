package com.maple.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.maple.model.ReconciliationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Reconciliation outcome for a single bank statement entry.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Reconciliation result for a single bank statement entry")
public class ReconciliationResultDto {

    @JsonProperty("entryId")
    @Schema(description = "Unique identifier of the bank statement entry")
    private UUID entryId;

    @JsonProperty("statementId")
    @Schema(description = "Statement identifier this entry belongs to")
    private String statementId;

    @JsonProperty("transactionDate")
    @Schema(description = "Transaction date from the bank statement")
    private LocalDate transactionDate;

    @JsonProperty("transactionReference")
    @Schema(description = "Bank-assigned reference number")
    private String transactionReference;

    @JsonProperty("paymentReference")
    @Schema(description = "Internal payment reference (if present in the bank statement)")
    private String paymentReference;

    @JsonProperty("amountCents")
    @Schema(description = "Transaction amount in cents")
    private Long amountCents;

    @JsonProperty("currency")
    @Schema(description = "ISO 4217 currency code")
    private String currency;

    @JsonProperty("debitCredit")
    @Schema(description = "DEBIT or CREDIT indicator")
    private String debitCredit;

    @JsonProperty("counterpartyName")
    @Schema(description = "Counterparty name from the bank statement")
    private String counterpartyName;

    @JsonProperty("reconciliationStatus")
    @Schema(description = "Outcome of the reconciliation matching")
    private ReconciliationStatus reconciliationStatus;

    @JsonProperty("matchedPaymentId")
    @Schema(description = "ID of the matched internal payment (when status is MATCHED, PARTIAL_MATCH or MANUALLY_MATCHED)")
    private UUID matchedPaymentId;

    @JsonProperty("matchedPaymentReference")
    @Schema(description = "Payment reference of the matched payment")
    private String matchedPaymentReference;

    @JsonProperty("matchConfidence")
    @Schema(description = "Confidence level of the automatic match: HIGH, MEDIUM, or null for manual/unmatched")
    private String matchConfidence;

    @JsonProperty("matchedAt")
    @Schema(description = "Timestamp when the match was recorded")
    private OffsetDateTime matchedAt;

    @JsonProperty("matchedBy")
    @Schema(description = "User ID who triggered or confirmed the match")
    private UUID matchedBy;
}
