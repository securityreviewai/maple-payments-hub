-- Create extension for UUID generation
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Create payment status enum
CREATE TYPE payment_status AS ENUM (
    'CREATED',
    'PENDING_APPROVAL', 
    'APPROVED',
    'REJECTED',
    'SUBMITTED',
    'SETTLED',
    'FAILED',
    'CANCELLED'
);

-- Create approval action enum
CREATE TYPE approval_action AS ENUM ('APPROVED', 'REJECTED');

-- Create actor type enum for audit events
CREATE TYPE actor_type AS ENUM ('USER', 'SYSTEM', 'EXTERNAL_PARTNER');

-- Create payments table
CREATE TABLE payments (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    payment_reference VARCHAR(64) NOT NULL UNIQUE,
    amount_cents BIGINT NOT NULL CHECK (amount_cents > 0),
    currency CHAR(3) NOT NULL DEFAULT 'USD',
    debtor_account VARCHAR(64) NOT NULL,
    creditor_account VARCHAR(64) NOT NULL,
    creditor_name VARCHAR(140),
    payment_purpose VARCHAR(500),
    status payment_status NOT NULL DEFAULT 'CREATED',
    initiated_by UUID NOT NULL,
    approved_by UUID,
    approval_required BOOLEAN NOT NULL DEFAULT false,
    submitted_at TIMESTAMPTZ,
    settled_at TIMESTAMPTZ,
    iso20022_filename VARCHAR(255),
    batch_id VARCHAR(64),
    raw_payload JSONB,
    idempotency_key VARCHAR(128),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT check_approval_logic CHECK (
        (approval_required = false) OR 
        (approval_required = true AND (status != 'APPROVED' OR approved_by IS NOT NULL))
    ),
    CONSTRAINT check_submitted_timestamp CHECK (
        (status != 'SUBMITTED' AND status != 'SETTLED' AND status != 'FAILED') OR 
        submitted_at IS NOT NULL
    ),
    CONSTRAINT check_settled_timestamp CHECK (
        (status != 'SETTLED') OR settled_at IS NOT NULL
    )
);

-- Create approvals table
CREATE TABLE approvals (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    payment_id UUID NOT NULL REFERENCES payments(id) ON DELETE CASCADE,
    approver_id UUID NOT NULL,
    action approval_action NOT NULL,
    note TEXT,
    two_factor_verified BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    UNIQUE(payment_id, approver_id, action)
);

-- Create audit_events table (append-only)
CREATE TABLE audit_events (
    id BIGSERIAL PRIMARY KEY,
    actor_type actor_type NOT NULL,
    actor_id VARCHAR(255),
    action VARCHAR(100) NOT NULL,
    target_type VARCHAR(100) NOT NULL,
    target_id VARCHAR(255) NOT NULL,
    details JSONB,
    request_id VARCHAR(128),
    session_id VARCHAR(128),
    ip_address INET,
    user_agent TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create users table for demo purposes
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    username VARCHAR(100) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    full_name VARCHAR(255) NOT NULL,
    roles TEXT[] NOT NULL DEFAULT '{}',
    is_active BOOLEAN NOT NULL DEFAULT true,
    requires_approval BOOLEAN NOT NULL DEFAULT false,
    approval_limit_cents BIGINT DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create payment batches table
CREATE TABLE payment_batches (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    batch_reference VARCHAR(64) NOT NULL UNIQUE,
    batch_type VARCHAR(50) NOT NULL DEFAULT 'OUTBOUND',
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    payment_count INTEGER NOT NULL DEFAULT 0,
    total_amount_cents BIGINT NOT NULL DEFAULT 0,
    iso20022_filename VARCHAR(255),
    sftp_uploaded_at TIMESTAMPTZ,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    submitted_at TIMESTAMPTZ,
    processed_at TIMESTAMPTZ
);

-- Create indexes for performance
CREATE INDEX idx_payments_payment_reference ON payments(payment_reference);
CREATE INDEX idx_payments_idempotency_key ON payments(idempotency_key) WHERE idempotency_key IS NOT NULL;
CREATE INDEX idx_payments_status ON payments(status);
CREATE INDEX idx_payments_initiated_by ON payments(initiated_by);
CREATE INDEX idx_payments_created_at ON payments(created_at);
CREATE INDEX idx_payments_submitted_at ON payments(submitted_at) WHERE submitted_at IS NOT NULL;

CREATE INDEX idx_approvals_payment_id ON approvals(payment_id);
CREATE INDEX idx_approvals_approver_id ON approvals(approver_id);

CREATE INDEX idx_audit_events_target ON audit_events(target_type, target_id);
CREATE INDEX idx_audit_events_actor ON audit_events(actor_type, actor_id);
CREATE INDEX idx_audit_events_action ON audit_events(action);
CREATE INDEX idx_audit_events_created_at ON audit_events(created_at);

CREATE INDEX idx_users_username ON users(username);
CREATE INDEX idx_users_email ON users(email);

CREATE INDEX idx_payment_batches_reference ON payment_batches(batch_reference);
CREATE INDEX idx_payment_batches_status ON payment_batches(status);
CREATE INDEX idx_payment_batches_created_at ON payment_batches(created_at);

-- Add relationship between payments and batches
ALTER TABLE payments ADD CONSTRAINT fk_payments_batch 
    FOREIGN KEY (batch_id) REFERENCES payment_batches(batch_reference);

-- Create function to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Create triggers for updated_at
CREATE TRIGGER update_payments_updated_at 
    BEFORE UPDATE ON payments 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_users_updated_at 
    BEFORE UPDATE ON users 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
