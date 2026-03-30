-- Add failure_reason to payments for failed payment diagnostics
ALTER TABLE payments ADD COLUMN IF NOT EXISTS failure_reason VARCHAR(500);
CREATE INDEX IF NOT EXISTS idx_payments_failure_reason ON payments (failure_reason) WHERE failure_reason IS NOT NULL;
