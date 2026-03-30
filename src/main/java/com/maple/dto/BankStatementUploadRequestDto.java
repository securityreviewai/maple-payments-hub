package com.maple.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request body for uploading a bank statement for reconciliation.
 *
 * A statement upload is idempotent per {@code statementId}: re-uploading
 * an already-processed statement will be rejected with HTTP 409.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Bank statement upload request")
public class BankStatementUploadRequestDto {

    @NotBlank
    @Size(max = 64)
    @Pattern(regexp = "[A-Za-z0-9_\\-]+", message = "Statement ID may only contain letters, digits, hyphens, and underscores")
    @JsonProperty("statementId")
    @Schema(description = "Unique identifier for this bank statement", example = "STMT-2024-01-15-001")
    private String statementId;

    @NotNull
    @NotEmpty(message = "At least one entry is required")
    @Size(max = 1000, message = "Maximum 1000 entries per upload to prevent resource exhaustion")
    @JsonProperty("entries")
    @Schema(description = "List of bank statement line entries (max 1000 per request)")
    private List<@Valid BankStatementEntryDto> entries;
}
