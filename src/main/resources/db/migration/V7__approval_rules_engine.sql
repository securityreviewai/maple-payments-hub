-- Approval rules engine: amount tiers, holiday calendars, escalation metadata on payments

CREATE TABLE holiday_calendars (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(128) NOT NULL,
    zone_id VARCHAR(64) NOT NULL DEFAULT 'America/New_York',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE holiday_dates (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    calendar_id UUID NOT NULL REFERENCES holiday_calendars(id) ON DELETE CASCADE,
    holiday_date DATE NOT NULL,
    label VARCHAR(255) NOT NULL,
    UNIQUE (calendar_id, holiday_date)
);

CREATE INDEX idx_holiday_dates_calendar_date ON holiday_dates(calendar_id, holiday_date);

-- Tiers: match rows where amount_cents > min_amount_cents AND (max_amount_cents IS NULL OR amount_cents <= max_amount_cents)
-- Higher sort_order evaluated first for overlapping ranges (use non-overlapping ranges in practice).
CREATE TABLE approval_amount_tiers (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    currency CHAR(3) NOT NULL,
    min_amount_cents BIGINT NOT NULL DEFAULT 0,
    max_amount_cents BIGINT,
    required_approvers SMALLINT NOT NULL CHECK (required_approvers IN (1, 2)),
    sort_order INT NOT NULL DEFAULT 0,
    UNIQUE (currency, sort_order)
);

CREATE INDEX idx_approval_amount_tiers_currency ON approval_amount_tiers(currency);

ALTER TABLE payments
    ADD COLUMN required_approvers SMALLINT NOT NULL DEFAULT 1 CHECK (required_approvers IN (1, 2)),
    ADD COLUMN first_approval_by UUID,
    ADD COLUMN first_approval_at TIMESTAMPTZ,
    ADD COLUMN holiday_calendar_id UUID REFERENCES holiday_calendars(id),
    ADD COLUMN escalation_due_at TIMESTAMPTZ,
    ADD COLUMN last_escalation_at TIMESTAMPTZ,
    ADD COLUMN escalation_level INT NOT NULL DEFAULT 0;

CREATE INDEX idx_payments_escalation ON payments(status, escalation_due_at)
    WHERE status = 'PENDING_APPROVAL' AND escalation_due_at IS NOT NULL;

-- Default US business calendar + sample 2026 US market holidays (America/New_York)
INSERT INTO holiday_calendars (id, name, zone_id)
VALUES (
    'a0000000-0000-4000-8000-000000000001',
    'US Federal (sample)',
    'America/New_York'
);

INSERT INTO holiday_dates (calendar_id, holiday_date, label) VALUES
('a0000000-0000-4000-8000-000000000001', '2026-01-01', 'New Year''s Day'),
('a0000000-0000-4000-8000-000000000001', '2026-01-19', 'Martin Luther King Jr. Day'),
('a0000000-0000-4000-8000-000000000001', '2026-02-16', 'Presidents'' Day'),
('a0000000-0000-4000-8000-000000000001', '2026-05-25', 'Memorial Day'),
('a0000000-0000-4000-8000-000000000001', '2026-07-03', 'Independence Day (observed)'),
('a0000000-0000-4000-8000-000000000001', '2026-09-07', 'Labor Day'),
('a0000000-0000-4000-8000-000000000001', '2026-11-26', 'Thanksgiving'),
('a0000000-0000-4000-8000-000000000001', '2026-12-25', 'Christmas Day');

-- USD tiers: <= $100,000.00 (10_000_000 cents): single control; above: dual control
INSERT INTO approval_amount_tiers (currency, min_amount_cents, max_amount_cents, required_approvers, sort_order)
VALUES
('USD', 0, 10000000, 1, 10),
('USD', 10000001, NULL, 2, 20);
