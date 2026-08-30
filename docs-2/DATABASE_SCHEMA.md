# DATABASE_SCHEMA.md
PostgreSQL 16. Every statement in `FLYWAY_MIGRATIONS/` is the executable form of this document.
**[DEMO]** marks values the client specification does not define.

## Conventions and the reasoning behind them

| Decision | Why |
|---|---|
| `uuid` PKs, `gen_random_uuid()` | spec 1.4 wants an opaque system ID for the case; UUIDs also let the service build an aggregate graph before the first flush. Cost: bigger index pages — accepted, this is not a high-throughput OLTP system. |
| Enums as `varchar` + `CHECK` | native PG enums make adding a value a lock-taking `ALTER TYPE` and map awkwardly in tests. `varchar + CHECK` is one cheap migration and matches JPA `@Enumerated(STRING)`. |
| `numeric(18,2)` money, `char(3)` currency | never floating point on contract sums. |
| `timestamptz` everywhere | spec 7.5.8 separates "when it happened" from "when it reached CRM"; a timezone-aware type is the only sane base even though field work is out of scope here. |
| `version bigint` only on mutable aggregates | append-only tables never take an UPDATE, so a version column there is dead weight. |
| `jsonb` for `form_data`, `payload`, `fields`, `checklist`, `rule_set_snapshot` | spec 5.2 / 7.6 make these route-configured, i.e. schemaless by requirement. They are payloads, never used as query predicates for business rules. |
| Business rules duplicated as DB `CHECK` | spec 20.3 and 13.5 talk about protection, not about "the service is careful". A constraint survives a bug in a new service method. |

## Table inventory (25 tables)

| # | Table | Purpose | Spec |
|---|---|---|---|
| 1 | `permission` | (section, action) pairs | 16.4 |
| 2 | `role` | 7 system roles | 3.1–3.9 |
| 3 | `role_permission` | auditable grants | 16.4, 16.10 |
| 4 | `department` | org tree | 16.12 |
| 5 | `position` | job title, **no permissions** | 16.13 |
| 6 | `app_user` | accounts | 16.3 |
| 7 | `user_role` | m:n | |
| 8 | `service` | вид услуги | 5.1, 16.5 |
| 9 | `service_submission_channel` | allowed channels | 1.3, 5.2 |
| 10 | `work_type` | performed-works catalogue | 8.2 |
| 11 | `external_stage` | applicant-facing labels | 5.11, 15.7 |
| 12 | `workflow` | route card, versioned | 5.2, 5.12 |
| 13 | `workflow_stage` | stage config | 5.3 |
| 14 | `workflow_transition` | data-driven edges | 5.3, 7.14 |
| 15 | `applicant` | typed applicant | 15.2 |
| 16 | `application` | заявка | 1.3, 4.3 |
| 17 | `electronic_case` | central object | 1.4, 1.5 |
| 18 | `case_participating_department` | 1.7, 7.2 | |
| 19 | `primary_check` | check attempts | 4.5–4.7 |
| 20 | `case_item` | pricing item composition | 12.6 |
| 21 | `case_stage` | runtime stage instance | 7.14 |
| 22 | `case_comment` | internal working comments | 13.5.1 |
| 23 | `price_rule` | **[DEMO]** tariffs | 12.2, 16.15 |
| 24 | `price_calculation` | immutable snapshot | 1.12, 12.4 |
| 25 | `price_calculation_line` | per-item breakdown | 12.6 |
| 26 | `contract` | contract data | 12.5, 12.10 |
| 27 | `payment` | payment state | 12.7 |
| 28 | `payment_confirmation` | append-only ledger | 12.7 |
| 29 | `task` | executive task | 7.3 |
| 30 | `task_result` | versioned result | 7.13 |
| 31 | `document` | document header | 6.5 |
| 32 | `document_version` | immutable version | 6.6, 13.5 |
| 33 | `approval_round` | endorsement round | 13.3 |
| 34 | `approval_task` | one per participant | 13.3, 13.4 |
| 35 | `performed_work` | works accounting | section 8 |
| 36 | `audit_log` | append-only journal | 1.19, 20.3 |
| 37 | `command_log` | request idempotency | plan §19 |

(37 tables including join tables — the "25" above counts aggregates.)

## Key constraints worth defending in an interview

### Uniqueness that encodes a business rule
| Constraint | Rule it enforces |
|---|---|
| `uq_case_application (application_id)` | spec 1.4 — exactly one case per registered application |
| `uq_workflow_code_version (code, version)` + partial `uq_workflow_one_active` | one ACTIVE version per route family; history preserved |
| `uq_case_stage (case_id, workflow_stage_id)` | **stage activation is idempotent by construction** |
| `uq_task_case_stage (case_stage_id)` | one live task per stage instance; a revision reuses it |
| `uq_task_result_version (task_id, version_no)` + partial `uq_task_result_live` | at most one live result; corrections make a new version |
| `uq_document_version (document_id, version_no)` + partial `uq_docver_signed_once` | a document is signed once |
| `uq_price_calc_one_active` (partial) | exactly one live calculation per case |
| `uq_performed_work_once (case_id, work_type_id, coalesce(case_stage_id, zero-uuid))` | **spec 8.5 — one real work counted once** |
| `uq_approval_round_one_open` (partial) | no two open rounds on the same version |
| `uq_payment_conf_external (payment_id, external_reference)` | double-clicked confirmation is rejected |
| `uq_command_log_key` | request-level idempotency |
| partial `uq_applicant_pinfl` / `uq_applicant_tin` | unique identity per applicant type, NULL-tolerant |

### CHECK constraints that carry spec text
- `ck_permission_action` — exactly the seven actions of 16.4, no more.
- `ck_role_code` — the seven roles, with 3.2 and 3.3 kept apart.
- `ck_applicant_individual` / `ck_applicant_legal` — 15.2 mandatory sets per type, in the DB.
- `ck_application_paper_registrar` — 1.3, a paper application must name its registrar.
- `ck_case_mode_audit` — 1.9, the mode always records who set it and when.
- `ck_contract_change_tracked` — **12.4**, overriding the calculated amount is impossible without
  `changed_by` + `changed_at`.
- `ck_payment_debt_consistent` — 12.7, `debt = contract - confirmed` can never drift.
- `ck_approval_task_reject_comment` — **13.4**, "не одобрено" without a reason cannot be stored.
- `ck_task_result_supersede` / `ck_docver_supersede` — 7.13 / 13.5, version ≥ 2 must say what it
  replaces and why.
- `ck_pc_reason_required` — 4.7, any non-acceptance is explained.
- `ck_audit_case_scope` — C4, administrative actions are the only ones allowed a NULL `case_id`.

### Indexes
Requested by the plan and kept: `application(applicant_id)`, `electronic_case(status)`,
`electronic_case(current_stage_id)`, `task(case_id)`, `task(assigned_user_id, status)`,
`audit_log(case_id, created_at)`.
Added where a real query needs them: `electronic_case(main_responsible_department_id, status)`
(department head dashboard, 18.3), `task(assigned_department_id, status)` (workload, 18.2),
partial `task(deadline) WHERE not finished` (overdue scan, 17.6), partial
`electronic_case(payment_due_at) WHERE waiting and not overdue` (payment expiry scheduler, 12.9),
`performed_work(executor_user_id, performed_at)` (works accounting, 8.3),
`case_stage(case_id, parallel_group) WHERE parallel_group IS NOT NULL` (the gating query),
`approval_task(participant_user_id, status)` (my endorsements inbox).

## Audit strategy (spec 20.3)

Four layers, in order of strength:

1. **No write API.** There is no generic "create audit event" endpoint. Audit rows are written only by
   domain services through a single `AuditWriter` port, inside the same transaction as the business
   change. `/api/audit` is read-only.
2. **Grants.** The application connects as `crm_app`, which holds `SELECT, INSERT` on `audit_log` and
   nothing else. `UPDATE`, `DELETE`, `TRUNCATE` are revoked. Flyway runs as the owner role, the
   application never does.
3. **Trigger.** `tr_audit_log_immutable` (row-level, UPDATE/DELETE) and `tr_audit_log_no_truncate`
   raise unconditionally. This is what makes the guarantee hold even against a mis-granted role.
4. **Hash chain.** `row_hash = sha256(prev_hash || canonical payload)` computed by a `BEFORE INSERT`
   trigger, so the application can neither forge nor skip it. `verify_audit_chain()` reports the first
   broken link. A DB superuser can still disable a trigger and rewrite a row — but not without
   breaking the chain, so tampering becomes **detectable**, which is the honest limit of what an
   application-tier design can promise. Real append-only storage (WORM, external log shipping) is
   named in the README as the production step beyond this slice.

Same technique, weaker form, applied to `payment_confirmation` (append-only), `performed_work`
(no delete), `document_version` and `task_result` (content columns immutable, lifecycle columns
movable — see `document_version_guard()` / `task_result_guard()`).

## Optimistic locking

`@Version` on `electronic_case`, `case_stage`, `task`, `document`, `approval_round`, `approval_task`,
`payment`, `contract`, `applicant`, `app_user`, `primary_check`.

The pattern: every workflow-mutating service method loads the `ElectronicCase` and touches it (status,
`current_stage_id` or `updated_at`), so **two concurrent commands on the same case always collide on
one row**. The loser gets `ObjectOptimisticLockingFailureException`, which the exception handler maps to
`409 CONCURRENT_MODIFICATION`. That gives a single, cheap, testable serialization point instead of
scattered pessimistic locks.

Where a retry must be transparent instead of a 409 — stage activation and task completion — the unique
constraints (`uq_case_stage`, `uq_task_case_stage`) plus a status pre-check make the operation
idempotent, so the second request returns the same result rather than an error. See
`WORKFLOW_ENGINE_DESIGN.md` §7.

Pessimistic locking (`SELECT ... FOR UPDATE`) is used in exactly one place: the parallel-group gating
query, which reads sibling `case_stage` rows before deciding to activate the dependent stage. Reason
in §6 of the workflow document.
