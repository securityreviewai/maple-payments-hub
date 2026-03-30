-- Saved search "views": share filters with a role; default workspace view per role.
ALTER TABLE saved_search_filters
    ADD COLUMN visibility VARCHAR(32) NOT NULL DEFAULT 'PRIVATE',
    ADD COLUMN shared_role VARCHAR(100) NULL;

ALTER TABLE saved_search_filters
    ADD CONSTRAINT chk_saved_search_visibility
        CHECK (
            (visibility = 'PRIVATE' AND shared_role IS NULL)
            OR (visibility = 'SHARED' AND shared_role IS NOT NULL)
        );

CREATE INDEX idx_saved_search_filters_shared_role ON saved_search_filters (shared_role)
    WHERE visibility = 'SHARED';

-- One optional default saved filter per app role (e.g. default payments list for ROLE_TREASURY_OPS).
CREATE TABLE role_default_workspace_views (
    role_name VARCHAR(100) PRIMARY KEY,
    saved_search_filter_id UUID REFERENCES saved_search_filters (id) ON DELETE SET NULL
);

CREATE INDEX idx_role_default_workspace_filter ON role_default_workspace_views (saved_search_filter_id);
