-- V7: execution block — tasks and versioned results (spec 7.2-7.3, 7.12-7.14, 13/§4.14-4.15)

CREATE TABLE task (
    id                     uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    case_id                uuid NOT NULL REFERENCES electronic_case(id) ON DELETE CASCADE,
    case_stage_id          uuid NOT NULL REFERENCES case_stage(id) ON DELETE CASCADE,
    workflow_stage_id      uuid NOT NULL REFERENCES workflow_stage(id) ON DELETE RESTRICT,
    title                  varchar(255) NOT NULL,
    description            text,
    assigned_department_id uuid NOT NULL REFERENCES department(id) ON DELETE RESTRICT,
    assigned_user_id       uuid REFERENCES app_user(id) ON DELETE RESTRICT,
    assigned_by_id         uuid REFERENCES app_user(id) ON DELETE RESTRICT,
    assigned_at            timestamptz,
    status                 varchar(30) NOT NULL DEFAULT 'CREATED',
    processing_mode        varchar(20),
    deadline               timestamptz,
    overdue                boolean NOT NULL DEFAULT false,
    started_at             timestamptz,
    completed_at           timestamptz,
    revision_count         int NOT NULL DEFAULT 0,
    version                bigint NOT NULL DEFAULT 0,
    created_at             timestamptz NOT NULL DEFAULT now(),
    updated_at             timestamptz NOT NULL DEFAULT now(),
    -- one live task per case stage; a revision reuses the same task row (spec 8.5)
    CONSTRAINT uq_task_case_stage UNIQUE (case_stage_id),
    CONSTRAINT ck_task_status CHECK (status IN
        ('CREATED','ASSIGNED','IN_PROGRESS','SUBMITTED_FOR_REVIEW','COMPLETED',
         'RETURNED_FOR_REVISION','CANCELLED')),
    CONSTRAINT ck_task_mode CHECK (processing_mode IS NULL OR processing_mode IN ('TRADITIONAL','EXPEDITED')),
    CONSTRAINT ck_task_assigned CHECK (status = 'CREATED' OR status = 'CANCELLED'
        OR (assigned_user_id IS NOT NULL AND assigned_at IS NOT NULL AND assigned_by_id IS NOT NULL)),
    CONSTRAINT ck_task_completed CHECK (status <> 'COMPLETED' OR completed_at IS NOT NULL)
);
CREATE INDEX ix_task_case ON task(case_id);
CREATE INDEX ix_task_assignee_status ON task(assigned_user_id, status);
CREATE INDEX ix_task_department_status ON task(assigned_department_id, status);
CREATE INDEX ix_task_deadline ON task(deadline) WHERE status NOT IN ('COMPLETED','CANCELLED');

-- Append-only version chain. An approved result is NEVER updated in place (spec 7.13).
CREATE TABLE task_result (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    task_id          uuid NOT NULL REFERENCES task(id) ON DELETE CASCADE,
    version_no       int NOT NULL,
    payload          jsonb NOT NULL DEFAULT '{}'::jsonb,
    summary          varchar(2000),
    status           varchar(20) NOT NULL DEFAULT 'DRAFT',
    author_id        uuid NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
    created_at       timestamptz NOT NULL DEFAULT now(),
    supersedes_id    uuid REFERENCES task_result(id) ON DELETE RESTRICT,
    revision_reason  varchar(2000),
    returned_by_id   uuid REFERENCES app_user(id) ON DELETE RESTRICT,
    returned_at      timestamptz,
    approved_by_id   uuid REFERENCES app_user(id) ON DELETE RESTRICT,
    approved_at      timestamptz,
    CONSTRAINT uq_task_result_version UNIQUE (task_id, version_no),
    CONSTRAINT ck_task_result_status CHECK (status IN
        ('DRAFT','SUBMITTED','APPROVED','SUPERSEDED','REJECTED')),
    CONSTRAINT ck_task_result_version_no CHECK (version_no >= 1),
    -- spec 7.13: a corrected result must carry the reason and point at what it replaces
    CONSTRAINT ck_task_result_supersede CHECK (
        version_no = 1 OR (supersedes_id IS NOT NULL AND revision_reason IS NOT NULL)),
    CONSTRAINT ck_task_result_approved CHECK (
        status <> 'APPROVED' OR (approved_by_id IS NOT NULL AND approved_at IS NOT NULL))
);
-- at most one live (non-superseded) result per task
CREATE UNIQUE INDEX uq_task_result_live ON task_result(task_id)
    WHERE status IN ('SUBMITTED','APPROVED');
CREATE INDEX ix_task_result_task ON task_result(task_id, version_no DESC);

-- Content of a result row is immutable; only the status/approval columns may change.
CREATE OR REPLACE FUNCTION task_result_guard() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'task_result rows cannot be deleted' USING ERRCODE = 'restrict_violation';
    END IF;
    IF NEW.payload::text <> OLD.payload::text
       OR NEW.version_no <> OLD.version_no
       OR NEW.author_id <> OLD.author_id
       OR NEW.created_at <> OLD.created_at THEN
        RAISE EXCEPTION 'task_result content is immutable; create a new version instead (spec 7.13)'
            USING ERRCODE = 'restrict_violation';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tr_task_result_guard BEFORE UPDATE OR DELETE ON task_result
    FOR EACH ROW EXECUTE FUNCTION task_result_guard();
CREATE TRIGGER tr_task_updated BEFORE UPDATE ON task
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
