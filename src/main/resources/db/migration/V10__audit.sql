-- V10: audit log (spec 1.19, 7.16.1, 12.11, 16.10, 18.12, 20.3)
-- PLAN_REVIEW C4: case_id must be NULLABLE — administrative events have no electronic case.
-- Spec 20.3 requires protection from modification and deletion by ANY user category, including ADMIN.
-- Three layers: no write API, restricted DB grants, and a hard trigger. Plus a hash chain so that
-- tampering by a superuser is at least DETECTABLE.

CREATE TABLE audit_log (
    id                   uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    seq                  bigserial NOT NULL,
    case_id              uuid REFERENCES electronic_case(id) ON DELETE RESTRICT,  -- NULLABLE
    task_id              uuid REFERENCES task(id) ON DELETE RESTRICT,
    user_id              uuid REFERENCES app_user(id) ON DELETE RESTRICT,          -- null = scheduler
    actor_role_code      varchar(40),
    actor_department_id  uuid REFERENCES department(id) ON DELETE RESTRICT,
    action               varchar(60) NOT NULL,
    entity_type          varchar(60) NOT NULL,
    entity_id            uuid,                     -- deliberately NO FK: weak, cross-entity reference
    old_value            jsonb,
    new_value            jsonb,
    reason               varchar(2000),
    ip_address           varchar(45),
    created_at           timestamptz NOT NULL DEFAULT now(),
    prev_hash            char(64),
    row_hash             char(64) NOT NULL,
    CONSTRAINT uq_audit_seq UNIQUE (seq),
    CONSTRAINT ck_audit_action CHECK (action IN (
        'CASE_CREATED','CASE_REGISTERED','PRIMARY_CHECK_COMPLETED','CATEGORY_ASSIGNED',
        'PRIMARY_CHECK_DECISION_RECORDED','ROUTE_ASSIGNED','ROUTE_CHANGED','PROCESSING_MODE_SET',
        'PRICE_CALCULATED','PRICE_RECALCULATED','PRICE_CONFIRMED','PRICE_CHANGED',
        'CONTRACT_RECORDED','CONTRACT_SENT','PAYMENT_CONFIRMED','PAYMENT_STATUS_CHANGED',
        'PAYMENT_OVERDUE','STAGE_ACTIVATED','STAGE_COMPLETED',
        'TASK_CREATED','TASK_ASSIGNED','TASK_REASSIGNED','TASK_STARTED','TASK_COMPLETED',
        'TASK_RETURNED','RESULT_VERSION_CREATED','RESULT_APPROVED',
        'DOCUMENT_CREATED','DOCUMENT_VERSION_CREATED','APPROVAL_ROUND_STARTED','APPROVAL_SENT',
        'APPROVAL_APPROVED','APPROVAL_REJECTED','APPROVAL_ROUND_COMPLETED','DOCUMENT_SIGNED',
        'PERFORMED_WORK_RECORDED','CASE_COMPLETED','CASE_REJECTED','CASE_RETURNED_TO_APPLICANT',
        'USER_CREATED','USER_UPDATED','USER_BLOCKED','DEPARTMENT_CHANGED','POSITION_CHANGED',
        'ROLE_PERMISSION_CHANGED','WORKFLOW_PUBLISHED',
        'WORKFLOW_RETIRED','PRICE_RULE_CHANGED','REFERENCE_DATA_CHANGED',
        'REPORTING_ACCESS_CHANGED','CONFIDENTIAL_DATA_ACCESSED')),
    -- an administrative event has no case; a case event must carry one
    -- USER_UPDATED/DEPARTMENT_CHANGED/POSITION_CHANGED added in Phase 4 (ASSUMPTIONS.md A15)
    CONSTRAINT ck_audit_case_scope CHECK (
        (action IN ('USER_CREATED','USER_UPDATED','USER_BLOCKED','DEPARTMENT_CHANGED',
                    'POSITION_CHANGED','ROLE_PERMISSION_CHANGED','WORKFLOW_PUBLISHED',
                    'WORKFLOW_RETIRED','PRICE_RULE_CHANGED','REFERENCE_DATA_CHANGED',
                    'REPORTING_ACCESS_CHANGED','CONFIDENTIAL_DATA_ACCESSED'))
        OR case_id IS NOT NULL)
);

CREATE INDEX ix_audit_case_created ON audit_log(case_id, created_at);
CREATE INDEX ix_audit_user_created ON audit_log(user_id, created_at);
CREATE INDEX ix_audit_action_created ON audit_log(action, created_at);
CREATE INDEX ix_audit_entity ON audit_log(entity_type, entity_id);

-- Hash chain: row_hash = sha256(prev_hash || canonical payload). Computed in the DB so the
-- application cannot forge it and cannot forget it.
CREATE OR REPLACE FUNCTION audit_log_chain() RETURNS trigger AS $$
DECLARE
    last_hash char(64);
BEGIN
    SELECT row_hash INTO last_hash FROM audit_log ORDER BY seq DESC LIMIT 1;
    NEW.prev_hash := last_hash;
    NEW.row_hash := encode(digest(
        coalesce(last_hash, '') || '|' ||
        NEW.id::text || '|' || coalesce(NEW.case_id::text,'') || '|' ||
        coalesce(NEW.user_id::text,'') || '|' || NEW.action || '|' ||
        NEW.entity_type || '|' || coalesce(NEW.entity_id::text,'') || '|' ||
        coalesce(NEW.old_value::text,'') || '|' || coalesce(NEW.new_value::text,'') || '|' ||
        NEW.created_at::text, 'sha256'), 'hex');
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tr_audit_log_chain BEFORE INSERT ON audit_log
    FOR EACH ROW EXECUTE FUNCTION audit_log_chain();

-- spec 20.3: no user category may modify or delete audit records.
CREATE TRIGGER tr_audit_log_immutable BEFORE UPDATE OR DELETE ON audit_log
    FOR EACH ROW EXECUTE FUNCTION forbid_mutation();

CREATE TRIGGER tr_audit_log_no_truncate BEFORE TRUNCATE ON audit_log
    FOR EACH STATEMENT EXECUTE FUNCTION forbid_mutation();

-- Layer 2: the application connects as crm_app, which is granted INSERT+SELECT on audit_log only.
-- The role is created here so the migration is self-contained; the password is supplied by env.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'crm_app') THEN
        CREATE ROLE crm_app LOGIN;
    END IF;
END $$;

GRANT USAGE ON SCHEMA public TO crm_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO crm_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO crm_app;

-- ... then take the dangerous rights on audit_log back.
REVOKE UPDATE, DELETE, TRUNCATE ON audit_log FROM crm_app;
GRANT SELECT, INSERT ON audit_log TO crm_app;
REVOKE UPDATE, DELETE ON payment_confirmation FROM crm_app;
REVOKE DELETE ON performed_work FROM crm_app;
REVOKE DELETE ON document_version FROM crm_app;
REVOKE DELETE ON task_result FROM crm_app;

ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO crm_app;

-- Verification helper for the integrity test and for a live "prove the log was not tampered with" demo.
CREATE OR REPLACE FUNCTION verify_audit_chain()
RETURNS TABLE(broken_seq bigint, expected char(64), actual char(64)) AS $$
DECLARE
    r record;
    running char(64) := NULL;
    calc char(64);
BEGIN
    FOR r IN SELECT * FROM audit_log ORDER BY seq LOOP
        calc := encode(digest(
            coalesce(running,'') || '|' || r.id::text || '|' || coalesce(r.case_id::text,'') || '|' ||
            coalesce(r.user_id::text,'') || '|' || r.action || '|' || r.entity_type || '|' ||
            coalesce(r.entity_id::text,'') || '|' || coalesce(r.old_value::text,'') || '|' ||
            coalesce(r.new_value::text,'') || '|' || r.created_at::text, 'sha256'), 'hex');
        IF calc <> r.row_hash THEN
            broken_seq := r.seq; expected := calc; actual := r.row_hash;
            RETURN NEXT;
        END IF;
        running := r.row_hash;
    END LOOP;
END;
$$ LANGUAGE plpgsql;
