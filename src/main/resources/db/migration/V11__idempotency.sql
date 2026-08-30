-- V11: request-level idempotency for workflow commands (MASTER_PLAN section 19)
-- Domain-level idempotency already comes from state checks + unique constraints
-- (uq_case_stage, uq_task_case_stage, uq_performed_work_once). This table closes the
-- remaining hole: a retried or double-clicked POST replaying the SAME command.

CREATE TABLE command_log (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    idempotency_key  varchar(120) NOT NULL,
    endpoint         varchar(200) NOT NULL,
    user_id          uuid REFERENCES app_user(id) ON DELETE RESTRICT,
    case_id          uuid REFERENCES electronic_case(id) ON DELETE CASCADE,
    request_hash     char(64) NOT NULL,
    response_status  int,
    response_body    jsonb,
    created_at       timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_command_log_key UNIQUE (idempotency_key)
);
CREATE INDEX ix_command_log_case ON command_log(case_id, created_at);

-- Contract: a mutating POST/PATCH may carry an Idempotency-Key header. First call inserts the row
-- inside the same transaction as the business change and stores the response. A replay with the same
-- key returns the stored response (200/201 as recorded). A replay with the same key but a DIFFERENT
-- request_hash is a client bug -> 409 IDEMPOTENCY_KEY_REUSED.
