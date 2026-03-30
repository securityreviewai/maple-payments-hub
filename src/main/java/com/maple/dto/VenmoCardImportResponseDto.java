package com.maple.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@AllArgsConstructor
@Schema(description = "Response after importing Venmo card data")
public class VenmoCardImportResponseDto {

    @JsonProperty("importId")
    @Schema(description = "Unique identifier for this import", example = "venmo-card-imp-a1b2c3d4")
    String importId;

    @JsonProperty("status")
    @Schema(description = "Status of the import", example = "accepted")
    String status;

    @JsonProperty("acceptedCount")
    @Schema(description = "Number of entries accepted and stored")
    int acceptedCount;
}
