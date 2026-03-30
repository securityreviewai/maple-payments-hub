package com.maple.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Resolved default saved-search view for the current user's workspace. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Default workspace view for the current session")
public class WorkspaceDefaultViewDto {

    @Schema(description = "App role whose default was applied (first match by configured priority)")
    @JsonProperty("appliedRole")
    private String appliedRole;

    @Schema(description = "Saved filter to apply; null if no default is configured for the user's roles")
    @JsonProperty("filter")
    private SavedSearchFilterDto filter;
}
