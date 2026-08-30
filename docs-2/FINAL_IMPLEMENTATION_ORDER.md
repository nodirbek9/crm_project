# FINAL_IMPLEMENTATION_ORDER.md

Hand this to local Claude Code as the execution plan. **13 phases, each with a hard Definition of Done.**
Do not start phase N+1 until phase N's DoD passes. Update `docs/IMPLEMENTATION_STATUS.md` after every
phase. Never ask "should I continue" — continue automatically.

Global rules for every phase:
* `mvn -q verify` must pass. A red build is never carried forward.
* `spring.jpa.hibernate.ddl-auto = validate`. Flyway owns the schema; entities must match it.
* No JPA entity leaves a controller. No business logic in a controller.
* Any ambiguity → pick an MVP assumption, append it to `docs/ASSUMPTIONS.md` marked **[DEMO]**, continue.
* Do not rewrite working code from an earlier phase without a failing test to justify it.

---

## Phase 1 — Skeleton, PostgreSQL, Flyway, Docker
Maven multi-package single module. Java 21, Spring Boot 3.x. Dependencies: web, data-jpa, security,
validation, flyway, postgresql, springdoc, mapstruct, lombok, jjwt, testcontainers, assertj.
Package root `uz.ithunter.crm`, packages per module (`auth, user, applicant, application, case,
workflow, document, task, approval, finance, work, audit, admin, shared`).
Profiles: `local`, `demo`, `test`, `prod`. `docker-compose.yml` with postgres 16 + backend.
Apply `V1__extensions_and_conventions.sql`.

**DoD:** `docker compose up` starts both containers; `/actuator/health` = UP; Flyway history has V1;
`mvn verify` green; `AbstractIntegrationTest` boots a Testcontainers postgres and passes an empty test.

## Phase 2 — Schema, all of it, up front
Apply `V2` … `V11` exactly as written in `FLYWAY_MIGRATIONS/`. No entities yet.

**DoD:** migration from empty to head succeeds on a fresh container; every table, constraint, index,
trigger and function from `DATABASE_SCHEMA.md` exists (assert with an information_schema test); N-01
passes; `verify_audit_chain()` is callable.

*Why the whole schema now: `ddl-auto=validate` then guards every entity you write afterwards. Writing
entities first and back-filling migrations is how drift starts.*

## Phase 3 — Security foundation
Entities: `Permission, Role, RolePermission, Department, Position, User`. `UserDetails` adapter,
`PermissionAuthorityResolver` (permissions resolved per request, not from the token), `JwtService`,
`JwtAuthenticationFilter`, `SecurityFilterChain`, BCrypt encoder, `AuthenticationEntryPoint` +
`AccessDeniedHandler` emitting the standard error body. Endpoints: `/auth/login`, `/auth/refresh`,
`/auth/logout`, `/auth/me`. `@RestControllerAdvice` + the full exception hierarchy from
`API_SPEC.md` §9.

**DoD:** S-01, S-02 pass; login returns a working token; a protected endpoint returns `401` without one
and `403` with an insufficient permission; the seeded grant matrix matches `SECURITY_SPEC.md` §3
(assert it in a test, do not eyeball it).

## Phase 4 — Reference data + admin CRUD
Entities: `Service, WorkType, ExternalStage`. Admin endpoints for users, departments, positions,
role permissions, services, work types, external stages. `AuditWriter` port + JPA implementation, wired
into every admin mutation.

**DoD:** A-04 passes (an admin event stores `case_id IS NULL` with old/new values); A-02, A-03 pass;
S-12 passes; admin CRUD round-trips through MockMvc.

## Phase 5 — Applicant & application
`Applicant` with `@GroupSequenceProvider`-driven validation groups, `Application` with
`submissionChannel`. Endpoints from `API_SPEC.md` §2 and §3 except `/register`.

**DoD:** I-03, I-04, I-05 pass, each asserting both the `400` from Bean Validation **and** the DB CHECK
rejecting a direct insert.

## Phase 6 — Workflow configuration & versioning
`Workflow, WorkflowStage, WorkflowTransition`. `WorkflowDefinitionService`: draft copy, publish-time
validation (the seven rules in `WORKFLOW_ENGINE_DESIGN.md` §2), publish, retire. Endpoints §8.
Seed workflow v1 (ACTIVE) and v2 (DRAFT) in `demo/V900`.

**DoD:** W-12 and C-07 pass; publishing v2 retires v1 in one transaction; a `PATCH` on a published
version is rejected; publish validation rejects an unreachable stage, a parallel group with no required
member, and an `approval_required` stage with no mode.

## Phase 7 — Case creation and the engine core
`ElectronicCase, CaseStage, CaseItem, PrimaryCheck, CaseComment`. `WorkflowEngine` with
`activateStage` / `completeStage` / `advance`, `TransitionEvaluator` + one `ConditionHandler` per
`ConditionType`, `StageActivator` per `StageType`, `DeadlineCalculator`, `ExternalStageMapper`.
`POST /applications/{id}/register`. `GET /cases`, `/cases/{id}`, `/cases/{id}/timeline`,
`/cases/{id}/tracking`. `POST /cases/{id}/primary-check`.

**DoD:** I-01, I-02, I-06 … I-09, U-01 … U-04, U-14, W-01, W-03 pass; registration is a single
transaction (assert one case + N stage rows + the audit trio); the tracking response is produced by
`ApplicantTrackingMapper`, and S-07 passes on the raw JSON.

## Phase 8 — Accounting, pricing, contract, payment
`PriceRule, PriceCalculation, PriceCalculationLine, Contract, Payment, PaymentConfirmation`.
`PriceCalculator`, `AccountingService`, `PaymentWaitingScheduler`. Endpoints §5.

**DoD:** U-05 … U-08, U-11, U-12, I-10 … I-15, S-03, S-04 pass. Specifically: the applicant gets `403`
on mode, changing the mode before confirmation auto-recalculates, and overriding the price preserves the
original calculation with `changed_by`/`changed_at` and a mandatory reason. The scheduler flags overdue
payment and creates a manager decision task — **and never rejects the case**.

## Phase 9 — Execution block
`Task, TaskResult`, `TaskFactory`, `TaskService`, `ExecutionStageActivator`. Endpoints §6.
Assignment, reassignment, start, submit result, approve result, return to revision, complete.

**DoD:** W-02, W-04 … W-10, S-08, S-09 pass; the parallel gate uses `SELECT ... FOR UPDATE` on the
sibling group; a completed task replay is a no-op that does not advance twice.

## Phase 10 — Documents, approvals, signing
`Document, DocumentVersion, ApprovalRound, ApprovalTask`. `DocumentService`, `ApprovalService`,
`SigningService`. Endpoints §7. Content storage is a local filesystem adapter behind a
`DocumentStoragePort` interface (**[DEMO]** — S3 named as the production swap).

**DoD:** D-01 … D-08, S-10 pass; there is no endpoint that mutates an existing version's content; only
`HEAD_OF_CERTIFICATION_BODY` can sign; a rejected round produces a new version and a new round with the
old remarks intact.

## Phase 11 — Performed works + full audit coverage
`PerformedWork`, `PerformedWorkRecorder` (upsert on the unique key), `ContractAmountBracketResolver`.
Endpoints §8. Audit every action in the `AuditAction` enum; verify none is unused.

**DoD:** PW-01, PW-02, PW-03, U-13, A-01, A-05 pass; a test asserts that every enum value is emitted by
at least one code path (or is explicitly listed as out-of-scope-in-this-slice).

## Phase 12 — Concurrency, idempotency, hardening
`command_log` handling via an `IdempotencyInterceptor`; `@Version` verified on every mutable aggregate;
`DataIntegrityViolationException` → business error code translation by constraint name; Swagger
finalised with auth, DTOs and error responses per endpoint.

**DoD:** C-01 … C-07 pass; `/swagger-ui.html` documents all 42 endpoints with their error codes;
the price and workflow schemas carry the demo-assumption notice.

## Phase 13 — Demo data, README, self-review
Verify `demo/V900` produces a fully walkable scenario. README with the 14 required sections, stating
plainly that tariffs, RED/YELLOW/GREEN criteria, deadlines, contract-amount boundary rules, department
and position names are demonstration assumptions. Then act as an independent Senior Reviewer:
write `docs/FINAL_REVIEW.md` classifying findings CRITICAL/HIGH/MEDIUM/LOW, fix all CRITICAL and HIGH,
rerun everything.

**DoD:** `docker compose up` on a clean machine → login as each demo user → walk the whole flow
(register → primary check → mode → price → contract → payment → identification → 3 parallel tasks →
final review → endorsement → signing → completed) via Swagger with no manual DB work; all 58 tests
green; N-02 passes; `FINAL_REVIEW.md` exists with no open CRITICAL or HIGH.

---

## Demo scenario to script into the README

1. `applicant@example.com` creates an application for `WASTE_CERTIFICATION` with 2 case items, submits.
2. `depthead.main@` registers it (or the applicant submits and staff registers) → case number appears.
3. `specialist1@` performs the primary check → `GREEN` / `ACCEPTED`.
4. `accountant@` sets `EXPEDITED` → price is auto-recalculated → confirms with a **different** actual
   amount + reason → records the contract → confirms full payment.
5. `depthead.main@` assigns IDENTIFICATION to `specialist1@`, who submits a result and completes it.
6. Three parallel tasks open. `depthead.lab@` assigns LABORATORY to `specialist2@`.
   **Complete only LABORATORY and EXPERT_REVIEW** → FINAL_REVIEW activates while the optional AUDIT is
   still open. That single step demonstrates spec 7.14 live.
7. `specialist1@` prepares the final document, `depthead.main@` opens a PARALLEL endorsement round with
   two required participants. One rejects with a comment → new version → new round → both approve.
8. `head@` signs. Case COMPLETED.
9. `GET /cases/{id}/performed-works` → each work counted once despite the revision.
10. `GET /audit/integrity` → intact. Then try `UPDATE audit_log` in psql → rejected.
11. `applicant@` calls `/cases/{id}/tracking` → sees only external stages, contract and payment.

Steps 6, 9, 10 and 11 are the four moments worth showing an interviewer. Rehearse those.
