-- Venmo-sourced card/transaction entries for user visualization (no raw card data).
CREATE TABLE venmo_card_entries (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL,
    transaction_date DATE NOT NULL,
    amount_cents BIGINT NOT NULL,
    description VARCHAR(500),
    entry_type VARCHAR(64) DEFAULT 'payment',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_venmo_amount CHECK (amount_cents != 0)
);

CREATE INDEX idx_venmo_card_entries_user_date ON venmo_card_entries(user_id, transaction_date);
CREATE INDEX idx_venmo_card_entries_user_id ON venmo_card_entries(user_id);
