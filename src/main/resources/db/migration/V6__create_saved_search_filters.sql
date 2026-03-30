-- Saved/favorite payment search filter sets per user.
-- Users can save named filter sets (e.g. "My pending", "Last 7 days failed")
-- and recall them by name to re-apply the same query params with one click.
CREATE TABLE saved_search_filters (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL,
    name VARCHAR(100) NOT NULL,
    query_params JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_saved_search_user_name UNIQUE (user_id, name),
    CONSTRAINT chk_saved_search_name_len CHECK (char_length(trim(name)) >= 1)
);

CREATE INDEX idx_saved_search_filters_user_id ON saved_search_filters(user_id);

CREATE TRIGGER update_saved_search_filters_updated_at
    BEFORE UPDATE ON saved_search_filters
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
