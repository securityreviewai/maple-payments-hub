package com.maple.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Single holiday date in a calendar")
public class HolidayDateDto {

    @JsonProperty("id")
    private UUID id;

    @JsonProperty("holidayDate")
    private LocalDate holidayDate;

    @JsonProperty("label")
    private String label;
}
