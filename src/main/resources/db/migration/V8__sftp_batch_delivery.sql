-- SFTP batch delivery tracking, retries, and partner receipt metadata

ALTER TABLE payment_batches
    ADD COLUMN IF NOT EXISTS sftp_delivery_status VARCHAR(32) NOT NULL DEFAULT 'PENDING_BUILD';

ALTER TABLE payment_batches
    ADD COLUMN IF NOT EXISTS sftp_remote_path VARCHAR(512);

ALTER TABLE payment_batches
    ADD COLUMN IF NOT EXISTS sftp_last_error TEXT;

ALTER TABLE payment_batches
    ADD COLUMN IF NOT EXISTS delivery_retry_count INT NOT NULL DEFAULT 0;

ALTER TABLE payment_batches
    ADD COLUMN IF NOT EXISTS max_delivery_retries INT NOT NULL DEFAULT 5;

ALTER TABLE payment_batches
    ADD COLUMN IF NOT EXISTS next_retry_at TIMESTAMPTZ;

ALTER TABLE payment_batches
    ADD COLUMN IF NOT EXISTS receipt_remote_path VARCHAR(512);

ALTER TABLE payment_batches
    ADD COLUMN IF NOT EXISTS receipt_received_at TIMESTAMPTZ;

ALTER TABLE payment_batches
    ADD COLUMN IF NOT EXISTS receipt_preview TEXT;

CREATE TABLE batch_delivery_attempts (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    batch_reference VARCHAR(64) NOT NULL REFERENCES payment_batches(batch_reference) ON DELETE CASCADE,
    attempt_number INT NOT NULL,
    phase VARCHAR(32) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at TIMESTAMPTZ,
    outcome VARCHAR(32) NOT NULL,
    detail_message TEXT,
    remote_path VARCHAR(512)
);

CREATE INDEX idx_batch_delivery_attempts_batch ON batch_delivery_attempts(batch_reference);
CREATE INDEX idx_payment_batches_sftp_delivery ON payment_batches(sftp_delivery_status);
CREATE INDEX idx_payment_batches_next_retry ON payment_batches(next_retry_at)
    WHERE next_retry_at IS NOT NULL;
