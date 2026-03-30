-- Create reconciliation status enum
CREATE TYPE reconciliation_status AS ENUM (
    'PENDING',
    'MATCHED',
    'PARTIAL_MATCH',
    'UNMATCHED',
    'DUPLICATE',
    'MANUALLY_MATCHED'
);

-- Create bank_statement_entries table
-- Each row represents one line from a bank statement uploaded for reconciliation.
CREATE TABLE bank_statement_entries (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),

    -- Groups all entries from the same uploaded statement file
    statement_id VARCHAR(64) NOT NULL,

    -- Bank-side transaction dates
    transaction_date DATE NOT NULL,
    value_date DATE,

    -- References from the bank
    transaction_reference VARCHAR(128),

    -- Our internal reference when it appears in the bank narrative
    payment_reference VARCHAR(64),

    -- Financial details
    amount_cents BIGINT NOT NULL CHECK (amount_cents > 0),
    currency CHAR(3) NOT NULL DEFAULT 'USD',
    debit_credit VARCHAR(6) NOT NULL CHECK (debit_credit IN ('DEBIT', 'CREDIT')),

    -- Counterparty info from the bank statement
    counterparty_account VARCHAR(64),
    counterparty_name VARCHAR(140),
    description VARCHAR(500),

    -- Reconciliation outcome
    reconciliation_status reconciliation_status NOT NULL DEFAULT 'PENDING',
    matched_payment_id UUID REFERENCES payments(id),
    match_confidence VARCHAR(20) CHECK (match_confidence IN ('HIGH', 'MEDIUM', 'LOW')),
    matched_at TIMESTAMPTZ,
    matched_by UUID,

    -- Provenance
    uploaded_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for common query patterns
CREATE INDEX idx_bse_statement_id ON bank_statement_entries(statement_id);
CREATE INDEX idx_bse_payment_reference ON bank_statement_entries(payment_reference)
    WHERE payment_reference IS NOT NULL;
CREATE INDEX idx_bse_reconciliation_status ON bank_statement_entries(reconciliation_status);
CREATE INDEX idx_bse_transaction_date ON bank_statement_entries(transaction_date);
CREATE INDEX idx_bse_matched_payment_id ON bank_statement_entries(matched_payment_id)
    WHERE matched_payment_id IS NOT NULL;
CREATE INDEX idx_bse_currency_amount ON bank_statement_entries(currency, amount_cents);

-- Auto-update updated_at on mutation
CREATE TRIGGER update_bse_updated_at
    BEFORE UPDATE ON bank_statement_entries
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
