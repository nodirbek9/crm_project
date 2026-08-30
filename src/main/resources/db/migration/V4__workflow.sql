-- V4: configurable, versioned workflow (spec 5.1-5.12, 16.11, 16.14)
-- A published workflow row and everything it owns are IMMUTABLE. Editing a route means
-- copy-on-write into version+1. Old cases keep pointing at the old version (spec 5.12, 16.11).

CREATE TABLE workflow (
    id                                    uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    service_id                            uuid NOT NULL REFERENCES service(id) ON DELETE RESTRICT,
    code                                  varchar(60) NOT NULL,
    version                               int NOT NULL,
    name                                  varchar(255) NOT NULL,
    description                           text,
    status                                varchar(20) NOT NULL DEFAULT 'DRAFT',
    main_responsible_department_id         uuid NOT NULL REFERENCES department(id) ON DELETE RESTRICT,
    expedited_allowed                     boolean NOT NULL DEFAULT false,  -- spec 5.7
    contract_required                     boolean NOT NULL DEFAULT true,
    payment_required                      boolean NOT NULL DEFAULT true,
    allow_execution_before_full_payment   boolean NOT NULL DEFAULT false,  -- spec 12.8
    payment_waiting_days                  int NOT NULL DEFAULT 10,         -- spec 12.9 [DEMO A5]
    total_deadline_days                   int,
    approval_required                     boolean NOT NULL DEFAULT false,  -- spec 5.10
    published_at                          timestamptz,
    published_by                          uuid REFERENCES app_user(id),
    created_at                            timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_workflow_code_version UNIQUE (code, version),
    CONSTRAINT ck_workflow_status CHECK (status IN ('DRAFT','ACTIVE','RETIRED')),
    CONSTRAINT ck_workflow_version_positive CHECK (version >= 1),
    CONSTRAINT ck_workflow_payment_waiting CHECK (payment_waiting_days > 0),
    CONSTRAINT ck_workflow_published CHECK (
        (status = 'DRAFT' AND published_at IS NULL) OR (status <> 'DRAFT' AND published_at IS NOT NULL))
);
-- At most one ACTIVE version per route family. New cases may only bind to an ACTIVE version.
CREATE UNIQUE INDEX uq_workflow_one_active ON workflow(code) WHERE status = 'ACTIVE';
CREATE INDEX ix_workflow_service ON workflow(service_id, status);

CREATE TABLE workflow_stage (
    id                        uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    workflow_id               uuid NOT NULL REFERENCES workflow(id) ON DELETE CASCADE,
    code                      varchar(60) NOT NULL,
    name                      varchar(255) NOT NULL,
    stage_type                varchar(40) NOT NULL,
    sequence                  int NOT NULL,
    parallel_group            varchar(60),                       -- same value = runs concurrently
    required                  boolean NOT NULL DEFAULT true,     -- spec 7.14 gating input
    external_stage_id         uuid NOT NULL REFERENCES external_stage(id) ON DELETE RESTRICT,
    internal_status_label     varchar(120) NOT NULL,             -- spec 5.3
    responsible_role_code     varchar(40) REFERENCES role(code),
    responsible_department_id uuid REFERENCES department(id) ON DELETE RESTRICT,
    assignment_mode           varchar(30) NOT NULL DEFAULT 'DEPARTMENT_HEAD_ASSIGNS',
    deadline_days             int,
    expedited_deadline_days   int,                               -- spec 5.8
    work_type_id              uuid REFERENCES work_type(id) ON DELETE RESTRICT,  -- spec 8
    produces_document_type    varchar(60),
    requires_result           boolean NOT NULL DEFAULT true,
    revision_allowed          boolean NOT NULL DEFAULT true,     -- spec 5.3
    approval_required         boolean NOT NULL DEFAULT false,    -- spec 5.10
    approval_mode             varchar(20),
    CONSTRAINT uq_workflow_stage_code UNIQUE (workflow_id, code),
    CONSTRAINT uq_workflow_stage_sequence UNIQUE (workflow_id, sequence),
    CONSTRAINT ck_stage_type CHECK (stage_type IN
        ('PRIMARY_CHECK','ROUTING','ACCOUNTING','PAYMENT_CONTROL','EXECUTION','ENDORSEMENT',
         'FINAL_REVIEW','SIGNING','COMPLETION','NON_APPLICABILITY_OPINION')),
    CONSTRAINT ck_stage_assignment_mode CHECK (assignment_mode IN
        ('DEPARTMENT_HEAD_ASSIGNS','ROUTE_FIXED_USER','AUTO_ROUND_ROBIN')),
    CONSTRAINT ck_stage_approval_mode CHECK (approval_mode IS NULL OR approval_mode IN ('SEQUENTIAL','PARALLEL')),
    CONSTRAINT ck_stage_approval_consistent CHECK (
        (approval_required = false) OR (approval_required = true AND approval_mode IS NOT NULL)),
    CONSTRAINT ck_stage_deadlines CHECK (
        (deadline_days IS NULL OR deadline_days > 0) AND
        (expedited_deadline_days IS NULL OR expedited_deadline_days > 0))
);
CREATE INDEX ix_workflow_stage_workflow ON workflow_stage(workflow_id, sequence);
CREATE INDEX ix_workflow_stage_parallel ON workflow_stage(workflow_id, parallel_group)
    WHERE parallel_group IS NOT NULL;

CREATE TABLE workflow_transition (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    workflow_id     uuid NOT NULL REFERENCES workflow(id) ON DELETE CASCADE,
    from_stage_id   uuid REFERENCES workflow_stage(id) ON DELETE CASCADE,  -- NULL = entry point
    to_stage_id     uuid NOT NULL REFERENCES workflow_stage(id) ON DELETE CASCADE,
    condition_type  varchar(50) NOT NULL,
    condition_value varchar(255),
    sequence        int NOT NULL DEFAULT 0,
    CONSTRAINT ck_transition_condition CHECK (condition_type IN
        ('ALWAYS','PRIMARY_CHECK_CATEGORY_IN','PRIMARY_CHECK_DECISION_IS','PROCESSING_MODE_IS',
         'PAYMENT_STATE_SATISFIED','ALL_REQUIRED_PARALLEL_TASKS_DONE','APPROVAL_ROUND_COMPLETED',
         'MANUAL_DECISION')),
    CONSTRAINT ck_transition_not_self CHECK (from_stage_id IS NULL OR from_stage_id <> to_stage_id),
    CONSTRAINT uq_transition UNIQUE (workflow_id, from_stage_id, to_stage_id, condition_type, condition_value)
);
CREATE INDEX ix_transition_from ON workflow_transition(workflow_id, from_stage_id, sequence);
CREATE UNIQUE INDEX uq_transition_single_entry ON workflow_transition(workflow_id)
    WHERE from_stage_id IS NULL;

-- Immutability of published configuration is enforced in the service layer (copy-on-write) and
-- verified by test W-11. A DB trigger is intentionally NOT used here: DRAFT rows must stay editable.
