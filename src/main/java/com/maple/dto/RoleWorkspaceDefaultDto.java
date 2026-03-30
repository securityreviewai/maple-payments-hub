package com.maple.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Role name and its configured default saved-search filter id (if any). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Per-role default workspace view mapping")
public class RoleWorkspaceDefaultDto {

    @JsonProperty("role")
    private String role;

    @JsonProperty("savedSearchFilterId")
    private UUID savedSearchFilterId;
}
