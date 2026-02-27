-- Add counterparty risk rating enum
CREATE TYPE counterparty_risk_rating AS ENUM ('LOW', 'MEDIUM', 'HIGH', 'RESTRICTED');

-- Create approval_policies table for dynamic maker-checker rules
CREATE TABLE approval_policies (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    policy_name VARCHAR(100) NOT NULL UNIQUE,
    description VARCHAR(500),
    is_active BOOLEAN NOT NULL DEFAULT true,
    priority INTEGER NOT NULL,
    
    -- Amount and currency filters
    min_amount_cents BIGINT,
    max_amount_cents BIGINT,
    currency CHAR(3),
    
    -- Geographic filters
    originating_country CHAR(2),
    destination_country CHAR(2),
    
    -- Risk filters
    max_counterparty_risk counterparty_risk_rating,
    payment_types TEXT[],
    
    -- Approval requirements (N-of-M pattern)
    required_approvers INTEGER NOT NULL CHECK (required_approvers > 0),
    eligible_approvers INTEGER NOT NULL CHECK (eligible_approvers >= required_approvers),
    required_roles TEXT[],
    excluded_roles TEXT[],
    require_dual_control BOOLEAN NOT NULL DEFAULT false,
    require_senior_approval BOOLEAN NOT NULL DEFAULT false,
    
    -- Time restrictions
    business_hours_only BOOLEAN NOT NULL DEFAULT false,
    valid_from TIMESTAMPTZ,
    valid_until TIMESTAMPTZ,
    
    -- Escalation settings
    escalation_hours INTEGER,
    escalation_roles TEXT[],
    
    -- Risk scoring
    risk_score_threshold INTEGER,
    additional_conditions JSONB,
    
    -- Audit fields
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID NOT NULL,
    last_modified_by UUID,
    
    CONSTRAINT check_amount_range CHECK (
        (min_amount_cents IS NULL OR max_amount_cents IS NULL) OR 
        (min_amount_cents <= max_amount_cents)
    ),
    CONSTRAINT check_date_range CHECK (
        (valid_from IS NULL OR valid_until IS NULL) OR 
        (valid_from <= valid_until)
    )
);

-- Create ACH batches table for NACHA file tracking
CREATE TABLE ach_batches (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    batch_reference VARCHAR(64) NOT NULL UNIQUE,
    sec_code VARCHAR(3) NOT NULL, -- PPD, CCD, CTX, WEB, etc.
    batch_type VARCHAR(20) NOT NULL DEFAULT 'OUTBOUND',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    
    -- Batch totals
    entry_count INTEGER NOT NULL DEFAULT 0,
    total_debit_amount_cents BIGINT NOT NULL DEFAULT 0,
    total_credit_amount_cents BIGINT NOT NULL DEFAULT 0,
    entry_hash VARCHAR(10),
    
    -- NACHA specific fields
    company_name VARCHAR(16),
    company_id VARCHAR(10),
    company_entry_description VARCHAR(10),
    effective_date DATE,
    settlement_date DATE,
    
    -- File information
    nacha_filename VARCHAR(255),
    file_creation_date DATE,
    file_creation_time TIME,
    immediate_destination VARCHAR(10),
    immediate_origin VARCHAR(10),
    
    -- Processing information
    created_by UUID NOT NULL,
    submitted_at TIMESTAMPTZ,
    transmitted_at TIMESTAMPTZ,
    acknowledged_at TIMESTAMPTZ,
    settled_at TIMESTAMPTZ,
    
    -- Audit fields
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT check_effective_date CHECK (effective_date >= CURRENT_DATE),
    CONSTRAINT check_totals CHECK (
        total_debit_amount_cents >= 0 AND 
        total_credit_amount_cents >= 0
    )
);

-- Create ACH entries table to link individual payments to batches
CREATE TABLE ach_entries (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    ach_batch_id UUID NOT NULL REFERENCES ach_batches(id) ON DELETE CASCADE,
    payment_id UUID NOT NULL REFERENCES payments(id) ON DELETE CASCADE,
    
    -- ACH specific fields
    transaction_code CHAR(2) NOT NULL,
    receiving_dfi_id CHAR(8) NOT NULL,
    check_digit CHAR(1) NOT NULL,
    dfi_account_number VARCHAR(17) NOT NULL,
    individual_id VARCHAR(15),
    individual_name VARCHAR(22),
    discretionary_data VARCHAR(2),
    addenda_indicator CHAR(1) NOT NULL DEFAULT '0',
    trace_number VARCHAR(15) NOT NULL,
    
    -- Entry sequence within batch
    entry_sequence INTEGER NOT NULL,
    
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    UNIQUE(ach_batch_id, entry_sequence),
    UNIQUE(payment_id) -- Each payment can only be in one ACH batch
);

-- Create addenda records table for ACH payment details
CREATE TABLE ach_addenda (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    ach_entry_id UUID NOT NULL REFERENCES ach_entries(id) ON DELETE CASCADE,
    
    addenda_type_code CHAR(2) NOT NULL DEFAULT '05',
    payment_related_info VARCHAR(80),
    addenda_sequence INTEGER NOT NULL,
    entry_detail_sequence INTEGER NOT NULL,
    
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    UNIQUE(ach_entry_id, addenda_sequence)
);

-- Create approval policy cache for performance
CREATE TABLE approval_policy_cache (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    cache_key VARCHAR(255) NOT NULL UNIQUE,
    applicable_policies JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL,
    
    CONSTRAINT check_expiry CHECK (expires_at > created_at)
);

-- Create indexes for performance

-- Approval policies indexes
CREATE INDEX idx_approval_policies_active ON approval_policies(is_active) WHERE is_active = true;
CREATE INDEX idx_approval_policies_priority ON approval_policies(priority, is_active);
CREATE INDEX idx_approval_policies_amount_range ON approval_policies(min_amount_cents, max_amount_cents) WHERE is_active = true;
CREATE INDEX idx_approval_policies_currency ON approval_policies(currency) WHERE currency IS NOT NULL;
CREATE INDEX idx_approval_policies_valid_date ON approval_policies(valid_from, valid_until) WHERE is_active = true;

-- ACH batches indexes
CREATE INDEX idx_ach_batches_reference ON ach_batches(batch_reference);
CREATE INDEX idx_ach_batches_status ON ach_batches(status);
CREATE INDEX idx_ach_batches_sec_code ON ach_batches(sec_code);
CREATE INDEX idx_ach_batches_effective_date ON ach_batches(effective_date);
CREATE INDEX idx_ach_batches_created_at ON ach_batches(created_at);
CREATE INDEX idx_ach_batches_created_by ON ach_batches(created_by);

-- ACH entries indexes
CREATE INDEX idx_ach_entries_batch_id ON ach_entries(ach_batch_id);
CREATE INDEX idx_ach_entries_payment_id ON ach_entries(payment_id);
CREATE INDEX idx_ach_entries_trace_number ON ach_entries(trace_number);

-- ACH addenda indexes
CREATE INDEX idx_ach_addenda_entry_id ON ach_addenda(ach_entry_id);

-- Policy cache indexes
CREATE INDEX idx_approval_policy_cache_key ON approval_policy_cache(cache_key);
CREATE INDEX idx_approval_policy_cache_expires ON approval_policy_cache(expires_at);

-- Add triggers for updated_at timestamps
CREATE TRIGGER update_approval_policies_updated_at 
    BEFORE UPDATE ON approval_policies 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_ach_batches_updated_at 
    BEFORE UPDATE ON ach_batches 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Insert default approval policies

-- Policy 1: High-value transactions (>$1M) require 2-of-3 senior approvals
INSERT INTO approval_policies (
    policy_name, description, priority, min_amount_cents, 
    required_approvers, eligible_approvers, required_roles, require_dual_control,
    created_by
) VALUES (
    'High Value Transactions',
    'Transactions over $1M require dual senior approval',
    1, 100000000,
    2, 3, 
    ARRAY['ROLE_TREASURY_MANAGER', 'ROLE_CLEARING'],
    true,
    uuid_generate_v4()
);

-- Policy 2: Medium-value transactions ($100k-$1M) require 1 manager approval
INSERT INTO approval_policies (
    policy_name, description, priority, min_amount_cents, max_amount_cents,
    required_approvers, eligible_approvers, required_roles, require_dual_control,
    created_by
) VALUES (
    'Medium Value Transactions',
    'Transactions $100k-$1M require manager approval',
    2, 10000000, 99999999,
    1, 2,
    ARRAY['ROLE_TREASURY_MANAGER'],
    true,
    uuid_generate_v4()
);

-- Policy 3: International wire transfers require enhanced approval
INSERT INTO approval_policies (
    policy_name, description, priority, payment_types,
    required_approvers, eligible_approvers, required_roles, require_senior_approval,
    max_counterparty_risk, business_hours_only,
    created_by
) VALUES (
    'International Wire Transfers',
    'All international wires require enhanced approval regardless of amount',
    1, ARRAY['FEDWIRE', 'SWIFT'],
    1, 2,
    ARRAY['ROLE_TREASURY_MANAGER', 'ROLE_CLEARING'],
    true, 'MEDIUM', true,
    uuid_generate_v4()
);

-- Policy 4: High-risk counterparties require additional approval
INSERT INTO approval_policies (
    policy_name, description, priority, max_counterparty_risk,
    required_approvers, eligible_approvers, required_roles, escalation_hours,
    created_by
) VALUES (
    'High Risk Counterparties',
    'Payments to high/restricted risk counterparties require additional approval',
    1, 'HIGH',
    2, 3,
    ARRAY['ROLE_TREASURY_MANAGER', 'ROLE_CLEARING'],
    4,
    uuid_generate_v4()
);

-- Clean up expired cache entries (for scheduled job)
CREATE OR REPLACE FUNCTION cleanup_expired_policy_cache()
RETURNS INTEGER AS $$
DECLARE
    deleted_count INTEGER;
BEGIN
    DELETE FROM approval_policy_cache WHERE expires_at < NOW();
    GET DIAGNOSTICS deleted_count = ROW_COUNT;
    RETURN deleted_count;
END;
$$ LANGUAGE plpgsql;
