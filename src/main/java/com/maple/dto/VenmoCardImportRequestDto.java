package com.maple.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to import Venmo-sourced card/transaction data for visualization")
public class VenmoCardImportRequestDto {

    @Valid
    @NotEmpty(message = "At least one entry is required")
    @Size(max = 1000, message = "Maximum 1000 entries per import")
    @JsonProperty("entries")
    @Schema(description = "List of transaction entries from Venmo export", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<VenmoCardImportEntryDto> entries;
}
