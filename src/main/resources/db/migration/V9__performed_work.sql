-- V9: performed-works accounting — specification section 8, entirely absent from the original plan.
-- One real work is recorded ONCE (8.5). Revision does not create a second record (8.5).
-- Bonus rates (8.6) are OUT OF SCOPE: the CRM only records the accounting basis.

CREATE TABLE performed_work (
    id                             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    case_id                        uuid NOT NULL REFERENCES electronic_case(id) ON DELETE CASCADE,
    work_type_id                   uuid NOT NULL REFERENCES work_type(id) ON DELETE RESTRICT,
    case_stage_id                  uuid REFERENCES case_stage(id) ON DELETE RESTRICT,
    workflow_stage_id              uuid REFERENCES workflow_stage(id) ON DELETE RESTRICT,
    service_id                     uuid NOT NULL REFERENCES service(id) ON DELETE RESTRICT,
    department_id                  uuid NOT NULL REFERENCES department(id) ON DELETE RESTRICT,
    executor_user_id               uuid NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
    processing_mode                varchar(20) NOT NULL,
    performed_at                   timestamptz NOT NULL,     -- when the work was actually done
    recorded_at                    timestamptz NOT NULL DEFAULT now(),
    recorded_by_id                 uuid REFERENCES app_user(id) ON DELETE RESTRICT,
    supporting_document_version_id uuid REFERENCES document_version(id) ON DELETE RESTRICT,
    invoice_reference              varchar(120),
    contract_amount_bracket        varchar(20),
    countable                      boolean NOT NULL DEFAULT true,
    CONSTRAINT ck_pw_mode CHECK (processing_mode IN ('TRADITIONAL','EXPEDITED')),
    -- spec 8.4: exactly the four ranges from the source table. Boundary rule is [DEMO] (A4).
    CONSTRAINT ck_pw_bracket CHECK (contract_amount_bracket IS NULL
        OR contract_amount_bracket IN ('LT_10M','M10_20M','M20_30M','GT_30M'))
);

-- THE central invariant of section 8.5: one performed work per (case, work type, stage).
-- A return-to-revision cycle re-uses this row; it never inserts a second one.
CREATE UNIQUE INDEX uq_performed_work_once
    ON performed_work(case_id, work_type_id, COALESCE(case_stage_id, '00000000-0000-0000-0000-000000000000'::uuid));

CREATE INDEX ix_performed_work_case ON performed_work(case_id);
CREATE INDEX ix_performed_work_executor ON performed_work(executor_user_id, performed_at);
CREATE INDEX ix_performed_work_department ON performed_work(department_id, performed_at);
CREATE INDEX ix_performed_work_type ON performed_work(work_type_id, performed_at);

-- spec 8.4: the bracket is mandatory exactly for green-certification expertise and audit.
CREATE OR REPLACE FUNCTION performed_work_bracket_guard() RETURNS trigger AS $$
DECLARE
    needs_bracket boolean;
BEGIN
    SELECT requires_contract_amount_bracket INTO needs_bracket
    FROM work_type WHERE id = NEW.work_type_id;
    IF needs_bracket AND NEW.contract_amount_bracket IS NULL THEN
        RAISE EXCEPTION 'work type requires a contract amount bracket (spec 8.4)'
            USING ERRCODE = 'check_violation';
    END IF;
    IF NOT needs_bracket AND NEW.contract_amount_bracket IS NOT NULL THEN
        RAISE EXCEPTION 'work type must not carry a contract amount bracket (spec 8.4)'
            USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tr_performed_work_bracket BEFORE INSERT OR UPDATE ON performed_work
    FOR EACH ROW EXECUTE FUNCTION performed_work_bracket_guard();

CREATE TRIGGER tr_performed_work_no_delete BEFORE DELETE ON performed_work
    FOR EACH ROW EXECUTE FUNCTION forbid_mutation();
