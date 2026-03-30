package com.maple.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request body for Venmo user data import.
 *
 * <p>Accepts a list of contacts or entries to import. Data is validated and
 * processed for Venmo-compatible format. Venmo's official API is limited;
 * this endpoint supports data preparation and validation.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to import user data into Venmo")
public class VenmoImportRequestDto {

    @NotNull
    @NotEmpty(message = "At least one entry is required")
    @Size(max = 100, message = "Maximum 100 entries per request to prevent abuse")
    @JsonProperty("entries")
    @Schema(description = "List of entries to import (contacts, etc.)", maxItems = 100)
    private List<@Valid VenmoImportEntryDto> entries;
}
