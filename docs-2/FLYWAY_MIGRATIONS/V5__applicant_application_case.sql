-- V5: applicant, application, electronic case and its owned parts
-- (spec 1.3, 1.4, 1.5, 4.3-4.7, 12.6, 13.5.1, 15.2)

CREATE TABLE applicant (
    id                       uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    type                     varchar(20) NOT NULL,
    -- individual
    last_name                varchar(120),
    first_name               varchar(120),
    middle_name              varchar(120),
    birth_date               date,
    passport_series          varchar(10),
    passport_number          varchar(20),
    pinfl                    varchar(20),
    -- legal entity
    org_name                 varchar(255),
    tin                      varchar(20),
    representative_full_name varchar(200),
    representative_position  varchar(200),
    power_of_attorney_ref    varchar(255),
    -- common
    address                  varchar(500) NOT NULL,
    phone                    varchar(40) NOT NULL,
    email                    varchar(255) NOT NULL,
    version                  bigint NOT NULL DEFAULT 0,
    created_at               timestamptz NOT NULL DEFAULT now(),
    updated_at               timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_applicant_type CHECK (type IN ('INDIVIDUAL','LEGAL_ENTITY')),
    -- spec 15.2: the two applicant types have different mandatory sets. Enforced in the DB as well
    -- as by Bean Validation groups, so a bad row cannot exist even via direct SQL.
    CONSTRAINT ck_applicant_individual CHECK (
        type <> 'INDIVIDUAL' OR (
            last_name IS NOT NULL AND first_name IS NOT NULL AND birth_date IS NOT NULL
            AND passport_series IS NOT NULL AND passport_number IS NOT NULL AND pinfl IS NOT NULL)),
    CONSTRAINT ck_applicant_legal CHECK (
        type <> 'LEGAL_ENTITY' OR (
            org_name IS NOT NULL AND tin IS NOT NULL
            AND representative_full_name IS NOT NULL AND representative_position IS NOT NULL))
);
CREATE UNIQUE INDEX uq_applicant_pinfl ON applicant(pinfl) WHERE pinfl IS NOT NULL;
CREATE UNIQUE INDEX uq_applicant_tin  ON applicant(tin)  WHERE tin  IS NOT NULL;

ALTER TABLE app_user
    ADD CONSTRAINT fk_app_user_applicant FOREIGN KEY (applicant_id)
    REFERENCES applicant(id) ON DELETE RESTRICT;

CREATE TABLE application (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    number              varchar(40) NOT NULL,
    applicant_id        uuid NOT NULL REFERENCES applicant(id) ON DELETE RESTRICT,
    service_id          uuid NOT NULL REFERENCES service(id) ON DELETE RESTRICT,
    submission_channel  varchar(30) NOT NULL,
    registered_by_id    uuid REFERENCES app_user(id) ON DELETE RESTRICT,
    submitted_at        timestamptz,
    registered_at       timestamptz,
    status              varchar(30) NOT NULL DEFAULT 'DRAFT',
    form_data           jsonb NOT NULL DEFAULT '{}'::jsonb,   -- route-defined mandatory fields, spec 5.2
    version             bigint NOT NULL DEFAULT 0,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_application_number UNIQUE (number),
    CONSTRAINT ck_application_channel CHECK (submission_channel IN
        ('PERSONAL_CABINET','SINGLE_WINDOW','OTHER_SERVICE','PAPER')),
    CONSTRAINT ck_application_status CHECK (status IN
        ('DRAFT','SUBMITTED','REGISTERED','RETURNED_TO_APPLICANT','CANCELLED')),
    -- spec 1.3: a paper application is registered by an authorised employee
    CONSTRAINT ck_application_paper_registrar CHECK (
        submission_channel <> 'PAPER' OR registered_by_id IS NOT NULL),
    CONSTRAINT ck_application_registered CHECK (
        status <> 'REGISTERED' OR registered_at IS NOT NULL)
);
CREATE INDEX ix_application_applicant ON application(applicant_id);
CREATE INDEX ix_application_service ON application(service_id);
CREATE INDEX ix_application_status ON application(status);

CREATE TABLE electronic_case (
    id                              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    case_number                     varchar(40) NOT NULL,
    application_id                  uuid NOT NULL REFERENCES application(id) ON DELETE RESTRICT,
    applicant_id                    uuid NOT NULL REFERENCES applicant(id) ON DELETE RESTRICT,
    service_id                      uuid NOT NULL REFERENCES service(id) ON DELETE RESTRICT,
    workflow_id                     uuid NOT NULL REFERENCES workflow(id) ON DELETE RESTRICT, -- pinned version
    status                          varchar(30) NOT NULL DEFAULT 'REGISTERED',
    current_stage_id                uuid REFERENCES workflow_stage(id) ON DELETE RESTRICT,
    primary_check_category          varchar(10),
    primary_check_decision          varchar(40),
    processing_mode                 varchar(20),
    processing_mode_set_by_id       uuid REFERENCES app_user(id) ON DELETE RESTRICT,
    processing_mode_set_at          timestamptz,
    main_responsible_department_id   uuid NOT NULL REFERENCES department(id) ON DELETE RESTRICT,
    due_at                          timestamptz,
    payment_due_at                  timestamptz,               -- spec 12.9
    payment_overdue                 boolean NOT NULL DEFAULT false,
    completed_at                    timestamptz,
    rejected_at                     timestamptz,
    rejection_reason                varchar(1000),
    version                         bigint NOT NULL DEFAULT 0, -- the workflow concurrency guard
    created_at                      timestamptz NOT NULL DEFAULT now(),
    updated_at                      timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_case_number UNIQUE (case_number),
    CONSTRAINT uq_case_application UNIQUE (application_id),     -- spec 1.4: exactly one case per application
    CONSTRAINT ck_case_status CHECK (status IN
        ('REGISTERED','PRIMARY_CHECK','PRIMARY_CHECK_DONE','IN_ACCOUNTING','WAITING_PAYMENT',
         'IN_EXECUTION','FINAL_REVIEW','ON_SIGNING','COMPLETED','RETURNED','REJECTED')),
    CONSTRAINT ck_case_category CHECK (primary_check_category IS NULL
        OR primary_check_category IN ('RED','YELLOW','GREEN')),
    CONSTRAINT ck_case_decision CHECK (primary_check_decision IS NULL
        OR primary_check_decision IN ('ACCEPTED','RETURNED_TO_APPLICANT','NON_APPLICABILITY_OPINION',
                                      'ROUTE_CHANGED','REJECTED')),
    CONSTRAINT ck_case_mode CHECK (processing_mode IS NULL OR processing_mode IN ('TRADITIONAL','EXPEDITED')),
    -- spec 1.9: the mode is always set by a user (accounting), never implicitly
    CONSTRAINT ck_case_mode_audit CHECK (
        processing_mode IS NULL OR (processing_mode_set_by_id IS NOT NULL AND processing_mode_set_at IS NOT NULL)),
    CONSTRAINT ck_case_rejected CHECK (status <> 'REJECTED' OR rejection_reason IS NOT NULL),
    CONSTRAINT ck_case_completed CHECK (status <> 'COMPLETED' OR completed_at IS NOT NULL)
);
CREATE INDEX ix_case_status ON electronic_case(status);
CREATE INDEX ix_case_current_stage ON electronic_case(current_stage_id);
CREATE INDEX ix_case_applicant ON electronic_case(applicant_id);
CREATE INDEX ix_case_workflow ON electronic_case(workflow_id);
CREATE INDEX ix_case_department ON electronic_case(main_responsible_department_id, status);
CREATE INDEX ix_case_payment_due ON electronic_case(payment_due_at)
    WHERE status = 'WAITING_PAYMENT' AND payment_overdue = false;

CREATE TABLE case_participating_department (
    case_id       uuid NOT NULL REFERENCES electronic_case(id) ON DELETE CASCADE,
    department_id uuid NOT NULL REFERENCES department(id) ON DELETE RESTRICT,
    PRIMARY KEY (case_id, department_id)
);

-- spec 4.5-4.7: the check itself is an auditable record; a return-and-resubmit adds a new row.
CREATE TABLE primary_check (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    case_id         uuid NOT NULL REFERENCES electronic_case(id) ON DELETE CASCADE,
    attempt_no      int NOT NULL DEFAULT 1,
    performed_by_id uuid NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
    performed_at    timestamptz NOT NULL DEFAULT now(),
    category        varchar(10) NOT NULL,
    decision        varchar(40) NOT NULL,
    reason          varchar(2000),
    checklist       jsonb NOT NULL DEFAULT '{}'::jsonb,
    new_workflow_id uuid REFERENCES workflow(id) ON DELETE RESTRICT,
    version         bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_primary_check_attempt UNIQUE (case_id, attempt_no),
    CONSTRAINT ck_pc_category CHECK (category IN ('RED','YELLOW','GREEN')),
    CONSTRAINT ck_pc_decision CHECK (decision IN
        ('ACCEPTED','RETURNED_TO_APPLICANT','NON_APPLICABILITY_OPINION','ROUTE_CHANGED','REJECTED')),
    -- every non-acceptance must be explained (spec 4.7, 15.8)
    CONSTRAINT ck_pc_reason_required CHECK (decision = 'ACCEPTED' OR reason IS NOT NULL),
    CONSTRAINT ck_pc_route_change CHECK (decision <> 'ROUTE_CHANGED' OR new_workflow_id IS NOT NULL)
);
CREATE INDEX ix_primary_check_case ON primary_check(case_id, attempt_no DESC);

-- spec 12.6: the item composition behind the contract sum MUST be stored.
CREATE TABLE case_item (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    case_id        uuid NOT NULL REFERENCES electronic_case(id) ON DELETE CASCADE,
    line_no        int NOT NULL,
    item_name      varchar(255) NOT NULL,
    item_code      varchar(60),
    quantity       numeric(14,3) NOT NULL DEFAULT 1,
    unit           varchar(20) NOT NULL DEFAULT 'PCS',
    object_address varchar(500),
    attributes     jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at     timestamptz NOT NULL DEFAULT now(),
    updated_at     timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_case_item_line UNIQUE (case_id, line_no),
    CONSTRAINT ck_case_item_quantity CHECK (quantity > 0)
);
CREATE INDEX ix_case_item_case ON case_item(case_id);

-- Runtime stage instance. The workflow config is immutable, so per-case stage state needs its own row.
-- This is what makes parallel gating and idempotent activation possible.
CREATE TABLE case_stage (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    case_id           uuid NOT NULL REFERENCES electronic_case(id) ON DELETE CASCADE,
    workflow_stage_id uuid NOT NULL REFERENCES workflow_stage(id) ON DELETE RESTRICT,
    status            varchar(20) NOT NULL DEFAULT 'PENDING',
    parallel_group    varchar(60),
    required          boolean NOT NULL DEFAULT true,
    activated_at      timestamptz,
    completed_at      timestamptz,
    due_at            timestamptz,
    overdue           boolean NOT NULL DEFAULT false,
    activation_count  int NOT NULL DEFAULT 0,
    version           bigint NOT NULL DEFAULT 0,
    created_at        timestamptz NOT NULL DEFAULT now(),
    updated_at        timestamptz NOT NULL DEFAULT now(),
    -- one row per (case, stage): re-activation is idempotent by construction
    CONSTRAINT uq_case_stage UNIQUE (case_id, workflow_stage_id),
    CONSTRAINT ck_case_stage_status CHECK (status IN
        ('PENDING','ACTIVE','COMPLETED','SKIPPED','RETURNED','CANCELLED')),
    CONSTRAINT ck_case_stage_activated CHECK (status = 'PENDING' OR activated_at IS NOT NULL),
    CONSTRAINT ck_case_stage_completed CHECK (status <> 'COMPLETED' OR completed_at IS NOT NULL)
);
CREATE INDEX ix_case_stage_case_status ON case_stage(case_id, status);
CREATE INDEX ix_case_stage_group ON case_stage(case_id, parallel_group) WHERE parallel_group IS NOT NULL;

-- spec 13.5.1 / 17.4 / 17.8: internal working comments. NOT the same thing as an endorsement remark
-- and never shown to the applicant.
CREATE TABLE case_comment (
    id                   uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    case_id              uuid NOT NULL REFERENCES electronic_case(id) ON DELETE CASCADE,
    document_version_id  uuid,                    -- FK added in V8
    author_id            uuid NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
    author_department_id uuid REFERENCES department(id) ON DELETE RESTRICT,
    body                 text NOT NULL,
    visibility           varchar(20) NOT NULL DEFAULT 'INTERNAL',
    created_at           timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_case_comment_visibility CHECK (visibility IN ('INTERNAL'))
);
CREATE INDEX ix_case_comment_case ON case_comment(case_id, created_at DESC);

CREATE TRIGGER tr_applicant_updated BEFORE UPDATE ON applicant
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER tr_application_updated BEFORE UPDATE ON application
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER tr_case_updated BEFORE UPDATE ON electronic_case
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER tr_case_item_updated BEFORE UPDATE ON case_item
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER tr_case_stage_updated BEFORE UPDATE ON case_stage
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
