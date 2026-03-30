package com.maple.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * High-level summary returned after a reconciliation run against a bank statement.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Summary of a reconciliation matching run")
public class ReconciliationSummaryDto {

    @JsonProperty("statementId")
    @Schema(description = "Statement identifier that was reconciled")
    private String statementId;

    @JsonProperty("totalEntries")
    @Schema(description = "Total number of entries in the statement")
    private int totalEntries;

    @JsonProperty("processedEntries")
    @Schema(description = "Number of entries processed during this run (PENDING at time of run)")
    private int processedEntries;

    @JsonProperty("matchedCount")
    @Schema(description = "Entries matched automatically with HIGH confidence")
    private int matchedCount;

    @JsonProperty("partialMatchCount")
    @Schema(description = "Entries with a MEDIUM confidence fuzzy match — need review")
    private int partialMatchCount;

    @JsonProperty("unmatchedCount")
    @Schema(description = "Entries with no matching internal payment found")
    private int unmatchedCount;

    @JsonProperty("duplicateCount")
    @Schema(description = "Entries where multiple candidate payments were found")
    private int duplicateCount;

    @JsonProperty("skippedCount")
    @Schema(description = "Entries skipped because they were already resolved")
    private int skippedCount;

    @JsonProperty("runAt")
    @Schema(description = "Timestamp when this reconciliation run was executed")
    private OffsetDateTime runAt;

    @JsonProperty("runBy")
    @Schema(description = "User ID who triggered this reconciliation run")
    private UUID runBy;

    @JsonProperty("results")
    @Schema(description = "Per-entry reconciliation outcomes")
    private List<ReconciliationResultDto> results;
}
