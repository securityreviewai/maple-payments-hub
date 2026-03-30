package com.maple.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * User-saved payment search filter set.
 * Stores a named filter with query parameters for one-click recall.
 */
@Entity
@Table(
    name = "saved_search_filters",
    uniqueConstraints = @UniqueConstraint(name = "uq_saved_search_user_name", columnNames = {"user_id", "name"}),
    indexes = @Index(name = "idx_saved_search_filters_user_id", columnList = "user_id"))
public class SavedSearchFilter {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    @NotNull
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false, length = 32)
    private SavedSearchVisibility visibility = SavedSearchVisibility.PRIVATE;

    /** When {@link SavedSearchVisibility#SHARED}, users with this role see the view. */
    @Column(name = "shared_role", length = 100)
    private String sharedRole;

    @Column(name = "name", nullable = false, length = 100)
    @NotBlank
    @Size(min = 1, max = 100)
    private String name;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "query_params", nullable = false, columnDefinition = "jsonb")
    @NotNull
    private Map<String, Object> queryParams = new HashMap<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private java.time.OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private java.time.OffsetDateTime updatedAt;

    @PrePersist
    @PreUpdate
    public void setUpdatedAt() {
        this.updatedAt = java.time.OffsetDateTime.now();
    }

    public SavedSearchFilter() {}

    public SavedSearchFilter(UUID userId, String name, Map<String, Object> queryParams) {
        this.userId = userId;
        this.name = name != null ? name.trim() : "";
        this.queryParams = queryParams != null ? queryParams : new HashMap<>();
        this.visibility = SavedSearchVisibility.PRIVATE;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public SavedSearchVisibility getVisibility() {
        return visibility;
    }

    public void setVisibility(SavedSearchVisibility visibility) {
        this.visibility = visibility != null ? visibility : SavedSearchVisibility.PRIVATE;
    }

    public String getSharedRole() {
        return sharedRole;
    }

    public void setSharedRole(String sharedRole) {
        this.sharedRole = sharedRole;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name != null ? name.trim() : "";
    }

    public Map<String, Object> getQueryParams() {
        return queryParams;
    }

    public void setQueryParams(Map<String, Object> queryParams) {
        this.queryParams = queryParams != null ? queryParams : new HashMap<>();
    }

    public java.time.OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(java.time.OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public java.time.OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(java.time.OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
