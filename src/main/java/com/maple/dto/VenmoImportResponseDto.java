package com.maple.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response for Venmo user data import.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response from Venmo import request")
public class VenmoImportResponseDto {

    @JsonProperty("importId")
    @Schema(description = "Unique identifier for this import", example = "venmo-imp-a1b2c3d4")
    private String importId;

    @JsonProperty("status")
    @Schema(description = "Import status", example = "accepted")
    private String status;

    @JsonProperty("acceptedCount")
    @Schema(description = "Number of entries accepted")
    private int acceptedCount;
}
