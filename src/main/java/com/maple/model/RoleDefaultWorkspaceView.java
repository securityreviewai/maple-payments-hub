package com.maple.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Default saved-search "view" to apply when a user with the given role opens the payments workspace.
 */
@Entity
@Table(name = "role_default_workspace_views")
public class RoleDefaultWorkspaceView {

    @Id
    @Column(name = "role_name", nullable = false, length = 100)
    private String roleName;

    @Column(name = "saved_search_filter_id")
    private UUID savedSearchFilterId;

    public RoleDefaultWorkspaceView() {}

    public RoleDefaultWorkspaceView(String roleName, UUID savedSearchFilterId) {
        this.roleName = roleName;
        this.savedSearchFilterId = savedSearchFilterId;
    }

    public String getRoleName() {
        return roleName;
    }

    public void setRoleName(String roleName) {
        this.roleName = roleName;
    }

    public UUID getSavedSearchFilterId() {
        return savedSearchFilterId;
    }

    public void setSavedSearchFilterId(UUID savedSearchFilterId) {
        this.savedSearchFilterId = savedSearchFilterId;
    }
}
