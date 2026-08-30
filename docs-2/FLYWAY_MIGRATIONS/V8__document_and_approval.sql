-- V8: documents, immutable versions, multi-participant endorsement
-- (spec 6.5, 6.6, 7.11, 13.1-13.7, 14.4-14.6)
-- PLAN_REVIEW C1: Document/DocumentVersion are MANDATORY, not optional.
-- PLAN_REVIEW C3: endorsement is one task PER PARTICIPANT.

CREATE TABLE document (
    id                 uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    case_id            uuid NOT NULL REFERENCES electronic_case(id) ON DELETE CASCADE,
    task_id            uuid REFERENCES task(id) ON DELETE SET NULL,
    document_type      varchar(60) NOT NULL,
    title              varchar(255) NOT NULL,
    status             varchar(30) NOT NULL DEFAULT 'DRAFT',
    current_version_id uuid,                       -- FK added after document_version exists
    created_by_id      uuid NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
    version            bigint NOT NULL DEFAULT 0,
    created_at         timestamptz NOT NULL DEFAULT now(),
    updated_at         timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_document_status CHECK (status IN
        ('DRAFT','UNDER_ENDORSEMENT','RETURNED_FOR_REVISION','ENDORSED','SIGNED','CANCELLED'))
);
CREATE INDEX ix_document_case ON document(case_id, document_type);
CREATE INDEX ix_document_task ON document(task_id);

CREATE TABLE document_version (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id      uuid NOT NULL REFERENCES document(id) ON DELETE CASCADE,
    version_no       int NOT NULL,
    content_ref      varchar(500) NOT NULL,
    content_hash     char(64) NOT NULL,            -- SHA-256, spec 7.13.1 integrity control
    file_name        varchar(255),
    mime_type        varchar(120),
    size_bytes       bigint,
    fields           jsonb NOT NULL DEFAULT '{}'::jsonb,   -- template placeholders, spec 6.3/6.4
    status           varchar(30) NOT NULL DEFAULT 'DRAFT',
    created_by_id    uuid NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
    created_at       timestamptz NOT NULL DEFAULT now(),
    supersedes_id    uuid REFERENCES document_version(id) ON DELETE RESTRICT,
    revision_reason  varchar(2000),
    signed_by_id     uuid REFERENCES app_user(id) ON DELETE RESTRICT,
    signed_at        timestamptz,
    CONSTRAINT uq_document_version UNIQUE (document_id, version_no),
    CONSTRAINT ck_docver_status CHECK (status IN
        ('DRAFT','UNDER_ENDORSEMENT','ENDORSED','REJECTED','SUPERSEDED','SIGNED')),
    CONSTRAINT ck_docver_version_no CHECK (version_no >= 1),
    -- spec 13.4/13.5: a corrected version must reference the one it replaces and say why
    CONSTRAINT ck_docver_supersede CHECK (
        version_no = 1 OR (supersedes_id IS NOT NULL AND revision_reason IS NOT NULL)),
    -- spec 14.4: only a signature carries a signer
    CONSTRAINT ck_docver_signed CHECK (
        status <> 'SIGNED' OR (signed_by_id IS NOT NULL AND signed_at IS NOT NULL)),
    CONSTRAINT ck_docver_hash CHECK (content_hash ~ '^[0-9a-f]{64}$')
);
CREATE INDEX ix_docver_document ON document_version(document_id, version_no DESC);
CREATE UNIQUE INDEX uq_docver_signed_once ON document_version(document_id) WHERE status = 'SIGNED';

ALTER TABLE document
    ADD CONSTRAINT fk_document_current_version FOREIGN KEY (current_version_id)
    REFERENCES document_version(id) ON DELETE RESTRICT;

ALTER TABLE case_comment
    ADD CONSTRAINT fk_case_comment_docver FOREIGN KEY (document_version_id)
    REFERENCES document_version(id) ON DELETE CASCADE;

-- spec 6.6 / 13.5: an already reviewed version is never overwritten. Only lifecycle columns move.
CREATE OR REPLACE FUNCTION document_version_guard() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'document_version rows cannot be deleted (spec 13.5)'
            USING ERRCODE = 'restrict_violation';
    END IF;
    IF NEW.content_ref <> OLD.content_ref
       OR NEW.content_hash <> OLD.content_hash
       OR NEW.version_no <> OLD.version_no
       OR NEW.document_id <> OLD.document_id
       OR NEW.created_by_id <> OLD.created_by_id
       OR NEW.created_at <> OLD.created_at
       OR NEW.fields::text <> OLD.fields::text THEN
        RAISE EXCEPTION 'document_version content is immutable; create version %+1 instead (spec 6.6, 13.5)',
            OLD.version_no USING ERRCODE = 'restrict_violation';
    END IF;
    IF OLD.status = 'SIGNED' AND NEW.status <> 'SIGNED' THEN
        RAISE EXCEPTION 'a signed document version cannot change status (spec 14.6)'
            USING ERRCODE = 'restrict_violation';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tr_docver_guard BEFORE UPDATE OR DELETE ON document_version
    FOR EACH ROW EXECUTE FUNCTION document_version_guard();

-- Endorsement round: targets a SPECIFIC document version (spec 13.5), sequential or parallel (13.3).
CREATE TABLE approval_round (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    document_version_id uuid NOT NULL REFERENCES document_version(id) ON DELETE CASCADE,
    case_id             uuid NOT NULL REFERENCES electronic_case(id) ON DELETE CASCADE,
    mode                varchar(20) NOT NULL,
    round_no            int NOT NULL DEFAULT 1,
    status              varchar(30) NOT NULL DEFAULT 'IN_PROGRESS',
    initiated_by_id     uuid NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
    initiated_at        timestamptz NOT NULL DEFAULT now(),
    completed_at        timestamptz,
    version             bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_approval_round UNIQUE (document_version_id, round_no),
    CONSTRAINT ck_approval_mode CHECK (mode IN ('SEQUENTIAL','PARALLEL')),
    CONSTRAINT ck_approval_round_status CHECK (status IN
        ('IN_PROGRESS','COMPLETED_APPROVED','COMPLETED_REJECTED','CANCELLED')),
    CONSTRAINT ck_approval_round_completed CHECK (
        status = 'IN_PROGRESS' OR completed_at IS NOT NULL)
);
CREATE UNIQUE INDEX uq_approval_round_one_open ON approval_round(document_version_id)
    WHERE status = 'IN_PROGRESS';
CREATE INDEX ix_approval_round_case ON approval_round(case_id, status);

-- spec 13.3: "по каждому участнику формируется отдельная задача"
CREATE TABLE approval_task (
    id                        uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    approval_round_id         uuid NOT NULL REFERENCES approval_round(id) ON DELETE CASCADE,
    participant_kind          varchar(20) NOT NULL,
    participant_user_id       uuid REFERENCES app_user(id) ON DELETE RESTRICT,
    participant_department_id uuid REFERENCES department(id) ON DELETE RESTRICT,
    required                  boolean NOT NULL DEFAULT true,
    sequence_no               int NOT NULL DEFAULT 0,
    status                    varchar(20) NOT NULL DEFAULT 'SENT',
    comment                   varchar(2000),
    decided_by_id             uuid REFERENCES app_user(id) ON DELETE RESTRICT,
    decided_at                timestamptz,
    due_at                    timestamptz,
    version                   bigint NOT NULL DEFAULT 0,
    created_at                timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_approval_task_kind CHECK (participant_kind IN
        ('USER','DEPARTMENT','APPLICANT','ACCOUNTING')),
    CONSTRAINT ck_approval_task_status CHECK (status IN
        ('SENT','IN_REVIEW','APPROVED','REJECTED','SKIPPED')),
    -- spec 13.4: "не одобрено" REQUIRES a reason. Enforced in the database, not just in a service.
    CONSTRAINT ck_approval_task_reject_comment CHECK (status <> 'REJECTED' OR comment IS NOT NULL),
    CONSTRAINT ck_approval_task_decided CHECK (
        status IN ('SENT','IN_REVIEW') OR (decided_by_id IS NOT NULL AND decided_at IS NOT NULL)),
    CONSTRAINT ck_approval_task_target CHECK (
        participant_user_id IS NOT NULL OR participant_department_id IS NOT NULL),
    CONSTRAINT uq_approval_task_participant UNIQUE
        (approval_round_id, participant_kind, participant_user_id, participant_department_id)
);
CREATE INDEX ix_approval_task_round ON approval_task(approval_round_id, sequence_no);
CREATE INDEX ix_approval_task_user_status ON approval_task(participant_user_id, status);
CREATE INDEX ix_approval_task_dept_status ON approval_task(participant_department_id, status);

CREATE TRIGGER tr_document_updated BEFORE UPDATE ON document
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
