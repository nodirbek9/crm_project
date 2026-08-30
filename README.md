# CRM Backend — IT Hunter Technical Assignment

A backend-only vertical slice of a certification-body CRM: application intake → registration →
primary check → routing → accounting/payment → parallel execution → documents/approval →
signing → completion, with a full audit trail. This is an interview deliverable: correctness,
a defensible architecture, and honest scope statements matter more than feature count.

## 1. Problem

A certification body (waste certification, in this slice) processes applications through a
multi-stage route: an applicant submits documents, staff perform a primary check, accounting sets
a price and records payment, specialists execute one or more work items (sometimes in parallel),
a final document is prepared, endorsed by multiple reviewers, and signed by the head of the body.
Every step must be attributable, auditable, and resistant to two staff members racing the same
action. The original specification leaves several business values undefined (tariffs, deadlines,
RED/YELLOW/GREEN criteria) — this slice implements the *mechanism* correctly and marks every
undefined value as a demonstration assumption rather than inventing a fake "official" rule.

## 2. Scope

**In scope** (implemented, tested): application intake and validation, case registration,
configurable/versioned workflow engine (sequential and parallel stages), primary check with five
decision outcomes, accounting (processing mode, pricing, contract, payment confirmation),
task-based execution with results, document versioning, multi-participant endorsement
(sequential/parallel), signing, performed-works accounting, a tamper-evident audit log,
request-level idempotency, and role/object-level authorization.

**Explicitly out of scope** — see [§13](#13-out-of-scope-declared-not-forgotten).

## 3. Architecture

Java 21, Spring Boot 4.1, PostgreSQL 16, Flyway-owned schema (`ddl-auto=validate` — the database
is the source of truth, entities must match it, never the other way round). One Maven module,
packages per business area (`auth, user, applicant, application, casemodule, workflow, document,
task, approval, finance, work, audit, admin, shared`).

Key architectural decisions:
- **The workflow is data, not code.** A route is a graph of `workflow_stage`/`workflow_transition`
  rows, versioned by `(code, version)`. Publishing a new version never rewrites history — cases
  already in flight keep pointing at the version they started on.
- **Two authorization layers everywhere.** (1) Does the caller hold the permission at all
  (`@PreAuthorize("hasAuthority(...)")`, checked against a live DB read every request, never JWT
  claims). (2) May this caller touch *this* object (`CaseAccessPolicy` and friends — the classic
  interview trap is a SPECIALIST who holds `TASK:EDIT` editing every task in the system).
- **Ports over cross-module coupling.** Later phases (payment state, approval-round completion,
  performed-work recording) implement small interfaces (`PaymentStateProvider`,
  `ApprovalStateProvider`, `StageWorkRecorder`) that the workflow engine calls via
  `ObjectProvider` — no bean means the gate stays shut, not a broken build.
- **Append-only where the domain says append-only.** `task_result` and `document_version` are
  never overwritten — a correction creates a new version. `audit_log` cannot be updated or deleted
  by anyone, including the schema owner, enforced by a DB trigger.
- **Idempotency and concurrency are explicit, not hoped for.** `@Version` optimistic locking on
  every mutable aggregate, `SELECT ... FOR UPDATE` for the parallel-task gate, an
  `Idempotency-Key`-driven `command_log` for retried requests, and a hash-chained audit log
  serialized against its own race under genuine concurrent writes.

See `docs-2/` for the full design record (domain model, schema, workflow engine design, security
spec, API spec, test matrix) — it is the authoritative source this implementation was built
against.

## 4. Domain model (summary)

`Applicant` (individual or legal entity) → `Application` → registered into `ElectronicCase`, which
owns `CaseStage` rows (one per workflow stage, materialised at registration) and `CaseItem` rows.
A case's `PrimaryCheck` assigns a RED/YELLOW/GREEN category and one of five decisions. Accounting
owns `PriceCalculation`/`PriceCalculationLine`, `Contract`, `Payment`/`PaymentConfirmation`.
Execution owns `Task`/`TaskResult` (versioned, append-only). Documents own `Document`/
`DocumentVersion` (versioned, append-only) and `ApprovalRound`/`ApprovalTask` (multi-participant,
sequential or parallel). `PerformedWork` records billable work exactly once per (case, work type,
stage). `AuditLog` records everything, hash-chained. Full field-level detail:
`docs-2/FINAL_DOMAIN_MODEL.md`.

## 5. Workflow

A route is: entry stage → ... → `COMPLETION`. Stage types: `PRIMARY_CHECK`, `ACCOUNTING`,
`PAYMENT_CONTROL`, `EXECUTION`, `FINAL_REVIEW`, `ENDORSEMENT`, `SIGNING`, `COMPLETION`. Transitions
carry a condition (`ALWAYS`, `PRIMARY_CHECK_DECISION_IS`, `PAYMENT_STATE_SATISFIED`,
`ALL_REQUIRED_PARALLEL_TASKS_DONE`, `APPROVAL_ROUND_COMPLETED`, `MANUAL_DECISION`) evaluated the
instant the source stage completes. Stages in the same `parallel_group` open together; the
dependent stage activates once every *required* sibling is done — an optional sibling (like the
demo's `AUDIT` stage) can be left open without blocking anything. The demo route:

```
PRIMARY_CHECK → ACCOUNTING → PAYMENT_CONTROL → IDENTIFICATION
  → { LABORATORY, EXPERT_REVIEW, AUDIT(optional) }   (parallel)
  → FINAL_REVIEW → ENDORSEMENT → SIGNING → COMPLETION
```

Full engine design (versioning rules, gating, idempotency): `docs-2/WORKFLOW_ENGINE_DESIGN.md`.

## 6. Security

Seven roles (`ADMIN, APPLICANT, ACCOUNTANT, HEAD_OF_CERTIFICATION_BODY, DEPARTMENT_HEAD,
SPECIALIST, OPERATOR`), seven permission actions (`VIEW, CREATE, EDIT, ENDORSE, APPROVE, SIGN,
BLOCK`) across thirteen sections — a fixed 7×13 matrix, seeded exactly as specified, never
diverged from (`docs-2/SECURITY_SPEC.md` §3, asserted row-for-row by
`SecurityGrantMatrixTest`). Stateless JWT (HS256, 15-minute access / 7-day refresh).
`HEAD_OF_CERTIFICATION_BODY` and `DEPARTMENT_HEAD` are deliberately kept as two distinct roles —
the single most-checked design decision in this project (see `docs-2/ARCHITECTURE_REVIEW.md`).

One deliberate, documented gap: the specification's own permission table grants nobody
`APPLICATION:EDIT`/`CASE:EDIT` (needed to register a case). This is intentional — a real
deployment grants it to `DEPARTMENT_HEAD` once, through the admin API, after first boot (see
[§8, step 0](#8-how-to-run)). Nothing in the seed data quietly diverges from the spec's matrix to
paper over this.

## 7. Out-of-scope items and every judgment call made along the way are in
`docs-2/ASSUMPTIONS.md`, each marked `[DEMO]`.

## 8. How to run

```bash
docker compose up --build
```

Starts PostgreSQL 16 and the backend (Flyway runs `db/migration` + `db/demo` automatically — no
manual DB work). Health: `GET http://localhost:8080/actuator/health`. API explorer:
`http://localhost:8080/swagger-ui.html`.

**Step 0 — one-time bootstrap (every fresh database):** log in as `admin@example.com`
(password below), then:

```
GET  /api/admin/roles/DEPARTMENT_HEAD/permissions
PATCH /api/admin/roles/DEPARTMENT_HEAD/permissions
     { "permissionCodes": [...current list..., "APPLICATION:EDIT", "CASE:EDIT"] }
```

This is the one API call every fresh deployment needs before the first case can be registered —
see [§6](#6-security) for why. `DemoScenarioWalkthroughTest` in the test suite performs exactly
this call (and reverts it) as part of an automated, repeatable proof the whole scenario works.

## 9. Test users

Every demo user shares password **`Demo12345!`** (a real bcrypt hash in `db/demo/V900`, never a
placeholder string).

| Email | Role | Department |
|---|---|---|
| `admin@example.com` | ADMIN | — |
| `depthead.main@example.com` | DEPARTMENT_HEAD | Основное |
| `depthead.lab@example.com` | DEPARTMENT_HEAD | Лаборатория |
| `specialist1@example.com` | SPECIALIST | Основное |
| `specialist2@example.com` | SPECIALIST | Лаборатория |
| `accountant@example.com` | ACCOUNTANT | Бухгалтерия |
| `head@example.com` | HEAD_OF_CERTIFICATION_BODY | Основное |
| `operator@example.com` | OPERATOR | — |
| `applicant@example.com` | APPLICANT | — (individual applicant) |

## 10. Demo scenario

The full walk from a new application to a signed, completed case:

1. `applicant@` creates an application for `WASTE_CERTIFICATION` with 2 case items, submits it.
2. `depthead.main@` registers it → a case number appears.
3. `specialist1@` performs the primary check → GREEN / ACCEPTED.
4. `accountant@` sets EXPEDITED mode → price auto-recalculates → confirms with a **different**
   actual amount and a reason → records the contract → confirms full payment.
5. `depthead.main@` assigns IDENTIFICATION to `specialist1@`, who submits a result and completes it.
6. Three parallel tasks open. `depthead.lab@` assigns LABORATORY to `specialist2@`. Complete only
   LABORATORY and EXPERT_REVIEW — FINAL_REVIEW activates while the optional AUDIT stays open. This
   single step demonstrates the parallel-gate rule live.
7. `specialist1@` prepares the final document; `depthead.main@` opens a PARALLEL endorsement round
   with two required participants. Both approve.
8. `head@` signs. The case is COMPLETED.
9. `GET /cases/{id}/performed-works` — each work item counted exactly once.
10. `GET /audit/integrity` — chain intact. An `UPDATE audit_log` in `psql` is rejected even for
    the schema owner.
11. `applicant@` calls `GET /cases/{id}/tracking` — sees only external-facing stage names,
    contract, and payment; nothing internal.

This entire sequence is executed automatically, end to end, by
`src/test/java/uz/ithunter/crm/integration/DemoScenarioWalkthroughTest.java` against the real
`db/demo` seed data and the real `/api/auth/login` endpoint — run it directly if you want proof
without clicking through Swagger by hand:

```bash
./mvnw test -Dtest=DemoScenarioWalkthroughTest
```

## 11. API

Full endpoint list, request/response shapes, status and error codes: `docs-2/API_SPEC.md`, and
live at `/swagger-ui.html` / `/v3/api-docs` once the app is running. Every error response shares
one shape (`timestamp, status, error, code, message, path, traceId, details`).

## 12. Testing

```bash
./mvnw clean verify
```

157 tests, all green — dependency-free unit tests (pricing, deadline, bracket resolution) next to
full-stack integration tests against a real PostgreSQL 16 Testcontainers instance (never H2 — the
tests assert on real triggers, constraints, and advisory locks). `docs-2/TEST_MATRIX.md` maps
every named test back to a spec clause. Concurrency is tested for real: several tests spin up
`ExecutorService` threads racing the same task, the same payment confirmation, or the same
approval round, and assert on the database's final state rather than which thread happened to
win — that is how two genuine PostgreSQL deadlocks and one audit-chain race condition were found
and fixed during Phase 12, not by code review.

## 13. Out of scope (declared, not forgotten)

Field work (GPS, photo metadata, graphic signature, offline upload), template-based document
generation, certificates/registry/QR/annulment, notifications, reporting/analytics beyond what the
API already exposes, external system integrations beyond a port interface, and the frontend
entirely. Each is a deliberate boundary of this vertical slice, not an oversight — see
`docs-2/IMPLEMENTATION_STATUS.md` for the authoritative, currently-true list.

## 14. Future extensions

The port-interface pattern (`PaymentStateProvider`, `ApprovalStateProvider`,
`DocumentStoragePort`) is exactly where a real deployment would plug in: a real payment gateway
webhook instead of manual confirmation, S3/blob storage instead of the local-filesystem demo
adapter, a notification service subscribing to the same audit events the log already records. The
workflow's own versioning already supports adding new stage types or routes without touching cases
in flight. `docs-2/ARCHITECTURE_REVIEW.md` discusses the five questions an interviewer is most
likely to ask about this design and answers them honestly, including residual risks.

---

**Everything marked `[DEMO]`** — tariffs, RED/YELLOW/GREEN criteria, deadlines, contract-amount
boundaries, department/position names, and the seed data itself — is a demonstration value chosen
to make the slice runnable end to end. None of it is presented as, or should be mistaken for, an
official client business rule. See `docs-2/ASSUMPTIONS.md` for the complete, numbered list with
the reasoning behind each one.
