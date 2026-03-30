package com.maple.controller.api.v1;

import com.maple.dto.RoleWorkspaceDefaultDto;
import com.maple.dto.SavedSearchFilterDto;
import com.maple.dto.WorkspaceDefaultViewDto;
import com.maple.model.SavedSearchVisibility;
import com.maple.service.search.SavedSearchFilterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for saved payment search filters and shareable "views" (role-targeted shared filters),
 * plus per-role default workspace views.
 */
@RestController
@RequestMapping("/api/v1/payments/search-filters")
@Tag(name = "Saved Search Filters", description = "Save and recall payment search filter sets and workspace views")
@SecurityRequirement(name = "OAuth2")
@SecurityRequirement(name = "BearerAuth")
public class SavedSearchFilterController {

    private final SavedSearchFilterService savedSearchFilterService;

    public SavedSearchFilterController(SavedSearchFilterService savedSearchFilterService) {
        this.savedSearchFilterService = savedSearchFilterService;
    }

    @GetMapping("/workspace-default")
    @Operation(
        summary = "Resolve default workspace view",
        description =
            "Returns the default saved filter for the current user, using the highest-priority role "
                + "that has a configured default (see role-defaults).",
        responses = {@ApiResponse(responseCode = "200", description = "Resolved default (filter may be null)")})
    @PreAuthorize(
        "hasAuthority('SCOPE_payments:read') or hasAuthority('ROLE_TREASURY_OPS') or hasAuthority('ROLE_TREASURY_MANAGER') "
            + "or hasAuthority('ROLE_CLEARING') or hasAuthority('ROLE_AUDITOR') or hasAuthority('ROLE_INTEGRATION')")
    public ResponseEntity<WorkspaceDefaultViewDto> getWorkspaceDefault(Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());
        WorkspaceDefaultViewDto dto =
                savedSearchFilterService.resolveWorkspaceDefault(userId, authorityStrings(authentication));
        return ResponseEntity.ok(dto);
    }

    @GetMapping("/role-defaults")
    @Operation(
        summary = "List per-role default workspace views",
        description = "Returns configured default saved filter id per app role (treasury managers).",
        responses = {@ApiResponse(responseCode = "200", description = "Mappings retrieved")})
    @PreAuthorize("hasAuthority('ROLE_TREASURY_MANAGER')")
    public ResponseEntity<List<RoleWorkspaceDefaultDto>> listRoleDefaults() {
        return ResponseEntity.ok(savedSearchFilterService.listRoleDefaults());
    }

    @PutMapping("/role-defaults/{role}")
    @Operation(
        summary = "Set default workspace view for a role",
        description =
            "Points a role at a SHARED saved filter targeted at that same role. Pass null body id to clear.",
        responses = {
            @ApiResponse(responseCode = "200", description = "Mapping updated"),
            @ApiResponse(responseCode = "400", description = "Invalid role or filter")
        })
    @PreAuthorize("hasAuthority('ROLE_TREASURY_MANAGER')")
    public ResponseEntity<RoleWorkspaceDefaultDto> setRoleDefault(
            @Parameter(description = "Role authority, e.g. ROLE_TREASURY_OPS") @PathVariable String role,
            @RequestBody(required = false) RoleDefaultRequest body) {
        UUID filterId = body != null ? body.getSavedSearchFilterId() : null;
        RoleWorkspaceDefaultDto dto = savedSearchFilterService.setRoleDefaultWorkspace(role, filterId);
        return ResponseEntity.ok(dto);
    }

    @GetMapping
    @Operation(
        summary = "List saved filters and shared views",
        description =
            "Returns the caller's saved filters plus SHARED views published for any of the caller's roles.",
        responses = {@ApiResponse(responseCode = "200", description = "Filters retrieved")})
    @PreAuthorize(
        "hasAuthority('SCOPE_payments:read') or hasAuthority('ROLE_TREASURY_OPS') or hasAuthority('ROLE_TREASURY_MANAGER') "
            + "or hasAuthority('ROLE_CLEARING') or hasAuthority('ROLE_AUDITOR') or hasAuthority('ROLE_INTEGRATION')")
    public ResponseEntity<List<SavedSearchFilterDto>> list(Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());
        List<SavedSearchFilterDto> filters =
                savedSearchFilterService.listVisibleForUser(userId, authorityStrings(authentication));
        return ResponseEntity.ok(filters);
    }

    @GetMapping("/{id}")
    @Operation(
        summary = "Get saved filter by ID",
        description = "Returns a saved filter the user owns or a SHARED view visible to one of their roles.",
        responses = {
            @ApiResponse(responseCode = "200", description = "Filter retrieved"),
            @ApiResponse(responseCode = "404", description = "Filter not found")
        })
    @PreAuthorize(
        "hasAuthority('SCOPE_payments:read') or hasAuthority('ROLE_TREASURY_OPS') or hasAuthority('ROLE_TREASURY_MANAGER') "
            + "or hasAuthority('ROLE_CLEARING') or hasAuthority('ROLE_AUDITOR') or hasAuthority('ROLE_INTEGRATION')")
    public ResponseEntity<SavedSearchFilterDto> getById(
            @Parameter(description = "Filter ID") @PathVariable UUID id,
            Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());
        return savedSearchFilterService
                .getByIdVisibleToUser(userId, id, authorityStrings(authentication))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @Operation(
        summary = "Save a filter or shared view",
        description =
            "Creates a saved filter. Use visibility=SHARED and sharedRole to publish a view to users with that role.",
        responses = {
            @ApiResponse(responseCode = "201", description = "Filter created"),
            @ApiResponse(responseCode = "400", description = "Invalid request or name already exists"),
            @ApiResponse(responseCode = "409", description = "Maximum saved filters reached")
        })
    @PreAuthorize(
        "hasAuthority('SCOPE_payments:read') or hasAuthority('ROLE_TREASURY_OPS') or hasAuthority('ROLE_TREASURY_MANAGER') "
            + "or hasAuthority('ROLE_CLEARING') or hasAuthority('ROLE_AUDITOR') or hasAuthority('ROLE_INTEGRATION')")
    public ResponseEntity<SavedSearchFilterDto> create(
            @Valid @RequestBody SaveFilterRequest request, Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());
        SavedSearchVisibility vis = SavedSearchFilterService.parseVisibility(request.getVisibility());
        if (vis == null) {
            vis = SavedSearchVisibility.PRIVATE;
        }
        SavedSearchFilterDto created =
                savedSearchFilterService.create(userId, request.getName(), request.getQueryParams(), vis, request.getSharedRole());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    @Operation(
        summary = "Update a saved filter",
        description = "Updates a saved filter. Only the owner can update (including sharing settings).",
        responses = {
            @ApiResponse(responseCode = "200", description = "Filter updated"),
            @ApiResponse(responseCode = "404", description = "Filter not found")
        })
    @PreAuthorize(
        "hasAuthority('SCOPE_payments:read') or hasAuthority('ROLE_TREASURY_OPS') or hasAuthority('ROLE_TREASURY_MANAGER') "
            + "or hasAuthority('ROLE_CLEARING') or hasAuthority('ROLE_AUDITOR') or hasAuthority('ROLE_INTEGRATION')")
    public ResponseEntity<SavedSearchFilterDto> update(
            @Parameter(description = "Filter ID") @PathVariable UUID id,
            @Valid @RequestBody SaveFilterRequest request,
            Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());
        SavedSearchVisibility vis = SavedSearchFilterService.parseVisibility(request.getVisibility());
        SavedSearchFilterDto updated =
                savedSearchFilterService.update(
                        userId, id, request.getName(), request.getQueryParams(), vis, request.getSharedRole());
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    @Operation(
        summary = "Delete a saved filter",
        description = "Deletes a saved filter. Only the owner can delete.",
        responses = {
            @ApiResponse(responseCode = "204", description = "Filter deleted"),
            @ApiResponse(responseCode = "404", description = "Filter not found")
        })
    @PreAuthorize(
        "hasAuthority('SCOPE_payments:read') or hasAuthority('ROLE_TREASURY_OPS') or hasAuthority('ROLE_TREASURY_MANAGER') "
            + "or hasAuthority('ROLE_CLEARING') or hasAuthority('ROLE_AUDITOR') or hasAuthority('ROLE_INTEGRATION')")
    public ResponseEntity<Void> delete(
            @Parameter(description = "Filter ID") @PathVariable UUID id,
            Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());
        savedSearchFilterService.delete(userId, id);
        return ResponseEntity.noContent().build();
    }

    private static Collection<String> authorityStrings(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());
    }

    @Schema(description = "Request to save or update a search filter")
    public static class SaveFilterRequest {
        @Schema(description = "Filter name", example = "My pending", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        private String name;

        @Schema(
            description =
                "Query params for payment search (paymentReference, status, startDate, endDate, etc.)")
        private Map<String, Object> queryParams;

        @Schema(description = "PRIVATE (default) or SHARED workspace view", example = "PRIVATE")
        private String visibility;

        @Schema(
            description = "When visibility is SHARED, target role (e.g. ROLE_TREASURY_OPS)",
            example = "ROLE_TREASURY_OPS")
        private String sharedRole;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Map<String, Object> getQueryParams() {
            return queryParams;
        }

        public void setQueryParams(Map<String, Object> queryParams) {
            this.queryParams = queryParams;
        }

        public String getVisibility() {
            return visibility;
        }

        public void setVisibility(String visibility) {
            this.visibility = visibility;
        }

        public String getSharedRole() {
            return sharedRole;
        }

        public void setSharedRole(String sharedRole) {
            this.sharedRole = sharedRole;
        }
    }

    @Schema(description = "Body for setting a role default workspace view")
    public static class RoleDefaultRequest {
        @Schema(description = "Saved filter id, or omit / null to clear the default")
        private UUID savedSearchFilterId;

        public UUID getSavedSearchFilterId() {
            return savedSearchFilterId;
        }

        public void setSavedSearchFilterId(UUID savedSearchFilterId) {
            this.savedSearchFilterId = savedSearchFilterId;
        }
    }
}
