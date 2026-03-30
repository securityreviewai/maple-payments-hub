package com.maple.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** DTO for saved search filter. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Saved payment search filter")
public class SavedSearchFilterDto {

    @Schema(description = "Filter ID")
    @JsonProperty("id")
    private java.util.UUID id;

    @Schema(description = "User-defined name (e.g. \"My pending\", \"Last 7 days failed\")")
    @JsonProperty("name")
    @NotBlank
    @Size(min = 1, max = 100)
    private String name;

    @Schema(
        description =
            "Query parameters for payment search (paymentReference, debtorAccount, creditorAccount, "
                + "status, initiatedBy, startDate, endDate, minAmountCents, maxAmountCents, currency, page, size)")
    @JsonProperty("queryParams")
    private Map<String, Object> queryParams;

    @Schema(description = "When the filter was created")
    @JsonProperty("createdAt")
    private java.time.OffsetDateTime createdAt;

    @Schema(description = "When the filter was last updated")
    @JsonProperty("updatedAt")
    private java.time.OffsetDateTime updatedAt;

    @Schema(description = "PRIVATE (default) or SHARED view with a role")
    @JsonProperty("visibility")
    private String visibility;

    @Schema(
        description = "When visibility is SHARED, the role that can see this view (e.g. ROLE_TREASURY_OPS)",
        nullable = true)
    @JsonProperty("sharedRole")
    private String sharedRole;

    @Schema(description = "User id of the owner who created this filter")
    @JsonProperty("ownerUserId")
    private java.util.UUID ownerUserId;

    @Schema(description = "True if the current user may edit or delete this filter")
    @JsonProperty("canEdit")
    private Boolean canEdit;
}
