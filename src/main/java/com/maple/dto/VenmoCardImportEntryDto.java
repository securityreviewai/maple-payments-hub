package com.maple.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Single Venmo-sourced card/transaction entry for import")
public class VenmoCardImportEntryDto {

    @NotNull(message = "Transaction date is required")
    @JsonProperty("transactionDate")
    @Schema(description = "Date of the transaction", example = "2024-01-15", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDate transactionDate;

    @NotNull(message = "Amount is required")
    @JsonProperty("amountCents")
    @Schema(description = "Amount in cents (positive for spend)", example = "2500", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long amountCents;

    @Size(max = 500)
    @JsonProperty("description")
    @Schema(description = "Optional description or memo", example = "Coffee shop")
    private String description;

    @Size(max = 64)
    @JsonProperty("entryType")
    @Schema(description = "Optional type/category (e.g. payment, transfer)", example = "payment")
    private String entryType;
}
