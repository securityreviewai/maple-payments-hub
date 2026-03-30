package com.maple.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Single entry for Venmo user data import (e.g., contacts to add or sync).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "A contact or entry to import into Venmo")
public class VenmoImportEntryDto {

    @NotBlank(message = "Display name is required")
    @Size(min = 1, max = 128)
    @JsonProperty("displayName")
    @Schema(description = "Display name for the contact", example = "Jane Doe")
    private String displayName;

    @NotBlank(message = "Identifier is required")
    @Size(min = 1, max = 256)
    @JsonProperty("identifier")
    @Schema(
            description = "Venmo username, email, or phone",
            example = "@jane-doe")
    private String identifier;
}
