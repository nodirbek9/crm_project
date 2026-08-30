-- V6: pricing, contract, payment (spec 1.11-1.14, 12.1-12.11)
-- CRM never processes money. Accounting only records confirmations (spec 1.14, 12.7).

-- ALL VALUES SEEDED HERE ARE [DEMO] (ASSUMPTIONS A3). Real tariffs come from the client.
CREATE TABLE price_rule (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    service_id      uuid REFERENCES service(id) ON DELETE RESTRICT,
    workflow_id     uuid REFERENCES workflow(id) ON DELETE RESTRICT,
    work_type_id    uuid REFERENCES work_type(id) ON DELETE RESTRICT,
    rule_type       varchar(30) NOT NULL,
    processing_mode varchar(20),
    base_price      numeric(18,2),
    coefficient     numeric(10,4),
    currency        char(3) NOT NULL DEFAULT 'UZS',
    valid_from      timestamptz NOT NULL DEFAULT now(),
    valid_to        timestamptz,
    demo            boolean NOT NULL DEFAULT true,
    active          boolean NOT NULL DEFAULT true,
    created_at      timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_price_rule_type CHECK (rule_type IN
        ('BASE_PER_ITEM','MODE_COEFFICIENT','ADDITIONAL_WORK_FEE','MINIMUM_TOTAL')),
    CONSTRAINT ck_price_rule_mode CHECK (processing_mode IS NULL
        OR processing_mode IN ('TRADITIONAL','EXPEDITED')),
    CONSTRAINT ck_price_rule_values CHECK (base_price IS NOT NULL OR coefficient IS NOT NULL),
    CONSTRAINT ck_price_rule_nonneg CHECK (
        (base_price IS NULL OR base_price >= 0) AND (coefficient IS NULL OR coefficient > 0)),
    CONSTRAINT ck_price_rule_validity CHECK (valid_to IS NULL OR valid_to > valid_from)
);
CREATE INDEX ix_price_rule_lookup ON price_rule(service_id, rule_type, processing_mode, active);
-- Rules are superseded by validity window, never edited: an old calculation stays reproducible.

-- Immutable calculation snapshot. A recalculation inserts a NEW row (spec 12.3, 12.4).
CREATE TABLE price_calculation (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    case_id           uuid NOT NULL REFERENCES electronic_case(id) ON DELETE CASCADE,
    calculation_no    int NOT NULL,
    processing_mode   varchar(20) NOT NULL,
    calculated_total  numeric(18,2) NOT NULL,
    currency          char(3) NOT NULL DEFAULT 'UZS',
    rule_set_snapshot jsonb NOT NULL,
    trigger_reason    varchar(30) NOT NULL,
    status            varchar(20) NOT NULL DEFAULT 'ACTIVE',
    supersedes_id     uuid REFERENCES price_calculation(id) ON DELETE RESTRICT,
    calculated_at     timestamptz NOT NULL DEFAULT now(),
    calculated_by_id  uuid REFERENCES app_user(id) ON DELETE RESTRICT,
    CONSTRAINT uq_price_calc_no UNIQUE (case_id, calculation_no),
    CONSTRAINT ck_price_calc_mode CHECK (processing_mode IN ('TRADITIONAL','EXPEDITED')),
    CONSTRAINT ck_price_calc_trigger CHECK (trigger_reason IN
        ('INITIAL','MODE_CHANGED','ITEMS_CHANGED','MANUAL_RECALC')),
    CONSTRAINT ck_price_calc_status CHECK (status IN ('ACTIVE','SUPERSEDED','CONFIRMED')),
    CONSTRAINT ck_price_calc_total CHECK (calculated_total >= 0)
);
-- exactly one live calculation per case
CREATE UNIQUE INDEX uq_price_calc_one_active ON price_calculation(case_id)
    WHERE status IN ('ACTIVE','CONFIRMED');
CREATE INDEX ix_price_calc_case ON price_calculation(case_id, calculation_no DESC);

CREATE TABLE price_calculation_line (
    id                   uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    price_calculation_id uuid NOT NULL REFERENCES price_calculation(id) ON DELETE CASCADE,
    line_no              int NOT NULL,
    case_item_id         uuid REFERENCES case_item(id) ON DELETE RESTRICT,
    price_rule_id        uuid REFERENCES price_rule(id) ON DELETE RESTRICT,
    description          varchar(255) NOT NULL,
    quantity             numeric(14,3) NOT NULL DEFAULT 1,
    unit_price           numeric(18,2) NOT NULL DEFAULT 0,
    coefficient          numeric(10,4) NOT NULL DEFAULT 1,
    line_total           numeric(18,2) NOT NULL,
    CONSTRAINT uq_price_line UNIQUE (price_calculation_id, line_no),
    CONSTRAINT ck_price_line_total CHECK (line_total >= 0)
);
CREATE INDEX ix_price_line_calc ON price_calculation_line(price_calculation_id);

CREATE TABLE contract (
    id                    uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    case_id               uuid NOT NULL REFERENCES electronic_case(id) ON DELETE CASCADE,
    contract_number       varchar(60),
    contract_date         date,
    calculated_amount     numeric(18,2) NOT NULL,     -- spec 12.4: original calculation, never overwritten
    actual_amount         numeric(18,2),
    amount_changed_by_id  uuid REFERENCES app_user(id) ON DELETE RESTRICT,
    amount_changed_at     timestamptz,
    amount_change_reason  varchar(1000),
    currency              char(3) NOT NULL DEFAULT 'UZS',
    sent                  boolean NOT NULL DEFAULT false,
    sent_at               timestamptz,
    sent_channel          varchar(20),
    invoice_reference     varchar(120),               -- spec 12.10: reference only, CRM issues nothing
    invoice_date          date,
    version               bigint NOT NULL DEFAULT 0,
    created_at            timestamptz NOT NULL DEFAULT now(),
    updated_at            timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_contract_case UNIQUE (case_id),
    CONSTRAINT ck_contract_amounts CHECK (
        calculated_amount >= 0 AND (actual_amount IS NULL OR actual_amount >= 0)),
    -- spec 12.4: overriding the calculated amount REQUIRES who and when
    CONSTRAINT ck_contract_change_tracked CHECK (
        actual_amount IS NULL OR actual_amount = calculated_amount
        OR (amount_changed_by_id IS NOT NULL AND amount_changed_at IS NOT NULL)),
    CONSTRAINT ck_contract_sent CHECK (
        sent = false OR (sent_at IS NOT NULL AND contract_number IS NOT NULL
                         AND contract_date IS NOT NULL AND sent_channel IS NOT NULL)),
    CONSTRAINT ck_contract_channel CHECK (sent_channel IS NULL OR sent_channel IN ('DIDOX','OTHER'))
);
CREATE UNIQUE INDEX uq_contract_number ON contract(contract_number) WHERE contract_number IS NOT NULL;

CREATE TABLE payment (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    case_id          uuid NOT NULL REFERENCES electronic_case(id) ON DELETE CASCADE,
    contract_id      uuid REFERENCES contract(id) ON DELETE RESTRICT,
    status           varchar(20) NOT NULL DEFAULT 'WAITING_PAYMENT',
    contract_amount  numeric(18,2) NOT NULL,
    confirmed_amount numeric(18,2) NOT NULL DEFAULT 0,
    debt_amount      numeric(18,2) NOT NULL DEFAULT 0,
    waiting_since    timestamptz,
    due_at           timestamptz,                    -- spec 12.9, configurable per route
    overdue          boolean NOT NULL DEFAULT false,
    version          bigint NOT NULL DEFAULT 0,
    created_at       timestamptz NOT NULL DEFAULT now(),
    updated_at       timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_payment_case UNIQUE (case_id),
    -- exactly the five states of spec 12.7
    CONSTRAINT ck_payment_status CHECK (status IN
        ('WAITING_PAYMENT','PAID','PARTIALLY_PAID','DEBT','NOT_CONFIRMED')),
    CONSTRAINT ck_payment_amounts CHECK (
        contract_amount >= 0 AND confirmed_amount >= 0 AND debt_amount >= 0
        AND confirmed_amount <= contract_amount),
    CONSTRAINT ck_payment_debt_consistent CHECK (debt_amount = contract_amount - confirmed_amount),
    CONSTRAINT ck_payment_paid CHECK (status <> 'PAID' OR confirmed_amount = contract_amount),
    CONSTRAINT ck_payment_partial CHECK (status <> 'PARTIALLY_PAID'
        OR (confirmed_amount > 0 AND confirmed_amount < contract_amount))
);
CREATE INDEX ix_payment_status ON payment(status);
CREATE INDEX ix_payment_due ON payment(due_at) WHERE status <> 'PAID' AND overdue = false;

-- Append-only ledger of accounting confirmations. No UPDATE, no DELETE.
CREATE TABLE payment_confirmation (
    id                 uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_id         uuid NOT NULL REFERENCES payment(id) ON DELETE CASCADE,
    amount             numeric(18,2) NOT NULL,
    confirmed_by_id    uuid NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
    confirmed_at       timestamptz NOT NULL DEFAULT now(),
    note               varchar(1000),
    external_reference varchar(120),
    CONSTRAINT ck_payment_conf_amount CHECK (amount > 0),
    -- idempotency guard against a double-clicked confirmation
    CONSTRAINT uq_payment_conf_external UNIQUE (payment_id, external_reference)
);
CREATE INDEX ix_payment_conf_payment ON payment_confirmation(payment_id, confirmed_at);
CREATE TRIGGER tr_payment_confirmation_immutable
    BEFORE UPDATE OR DELETE ON payment_confirmation
    FOR EACH ROW EXECUTE FUNCTION forbid_mutation();

CREATE TRIGGER tr_contract_updated BEFORE UPDATE ON contract
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER tr_payment_updated BEFORE UPDATE ON payment
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
