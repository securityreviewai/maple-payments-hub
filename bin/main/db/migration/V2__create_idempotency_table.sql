-- Create idempotency table for request deduplication
CREATE TABLE idempotency_records (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    idempotency_key VARCHAR(128) NOT NULL,
    endpoint_path VARCHAR(255) NOT NULL,
    http_method VARCHAR(10) NOT NULL,
    request_hash VARCHAR(64) NOT NULL, -- SHA-256 of request body
    response_status INTEGER,
    response_body TEXT,
    response_headers JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL DEFAULT (CURRENT_TIMESTAMP + INTERVAL '48 hours'),
    
    UNIQUE(idempotency_key, endpoint_path, http_method)
);

-- Index for efficient cleanup of expired records
CREATE INDEX idx_idempotency_expires_at ON idempotency_records(expires_at);
CREATE INDEX idx_idempotency_key ON idempotency_records(idempotency_key);

-- Create webhook events table for tracking inbound partner notifications
CREATE TABLE webhook_events (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    source_system VARCHAR(100) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    event_id VARCHAR(255), -- External event ID from partner
    payload JSONB NOT NULL,
    signature VARCHAR(512), -- HMAC signature
    signature_verified BOOLEAN NOT NULL DEFAULT false,
    processed BOOLEAN NOT NULL DEFAULT false,
    processing_error TEXT,
    idempotency_key VARCHAR(128),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMPTZ,
    
    UNIQUE(source_system, event_id) DEFERRABLE INITIALLY DEFERRED
);

CREATE INDEX idx_webhook_events_source_type ON webhook_events(source_system, event_type);
CREATE INDEX idx_webhook_events_processed ON webhook_events(processed);
CREATE INDEX idx_webhook_events_created_at ON webhook_events(created_at);
CREATE INDEX idx_webhook_events_idempotency ON webhook_events(idempotency_key) WHERE idempotency_key IS NOT NULL;
