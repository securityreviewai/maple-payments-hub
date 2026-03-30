package com.maple.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * A single line entry from a bank statement, submitted for reconciliation matching.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Single bank statement entry for reconciliation")
public class BankStatementEntryDto {

    @NotNull
    @JsonProperty("transactionDate")
    @Schema(description = "Date of the bank transaction", example = "2024-01-15")
    private LocalDate transactionDate;

    @JsonProperty("valueDate")
    @Schema(description = "Value / settlement date", example = "2024-01-15")
    private LocalDate valueDate;

    @Size(max = 128)
    @JsonProperty("transactionReference")
    @Schema(description = "Bank-assigned reference for this transaction", example = "TXN-20240115-001")
    private String transactionReference;

    @Size(max = 64)
    @JsonProperty("paymentReference")
    @Schema(description = "Internal payment reference when present in bank narrative", example = "PAY-2024-001234")
    private String paymentReference;

    @NotNull
    @Positive(message = "Amount must be greater than zero")
    @JsonProperty("amountCents")
    @Schema(description = "Transaction amount in cents (positive integer)", example = "150000")
    private Long amountCents;

    @NotBlank
    @Size(min = 3, max = 3)
    @Pattern(regexp = "[A-Z]{3}", message = "Currency must be a 3-letter ISO 4217 code in uppercase")
    @JsonProperty("currency")
    @Schema(description = "ISO 4217 currency code", example = "USD")
    private String currency;

    @NotBlank
    @Pattern(regexp = "DEBIT|CREDIT", message = "Must be DEBIT or CREDIT")
    @JsonProperty("debitCredit")
    @Schema(description = "DEBIT or CREDIT from the account-holder's perspective", example = "DEBIT")
    private String debitCredit;

    @Size(max = 64)
    @JsonProperty("counterpartyAccount")
    @Schema(description = "Counterparty account identifier", example = "GB29NWBK60161331926819")
    private String counterpartyAccount;

    @Size(max = 140)
    @JsonProperty("counterpartyName")
    @Schema(description = "Counterparty name as shown in the bank statement", example = "ABC Corporation Ltd")
    private String counterpartyName;

    @Size(max = 500)
    @JsonProperty("description")
    @Schema(description = "Transaction narrative / description from the bank", example = "Payment ref PAY-2024-001234")
    private String description;
}
