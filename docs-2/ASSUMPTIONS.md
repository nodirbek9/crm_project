# ASSUMPTIONS

Every value below is a **demonstration assumption**. The client specification does not define
final business values for these points. Nothing here should be read as an official client rule.

## A1. Primary check category criteria (spec 1.5, 4.6)
The spec names RED / YELLOW / GREEN but defines no criteria. DEMO rules:
- GREEN: all required application fields filled and all required documents attached.
- YELLOW: optional/secondary data missing -> normal route plus an additional review stage.
- RED: a required document is missing or applicant data is inconsistent -> enhanced verification;
  the reviewing specialist chooses one of the five decisions of spec 4.7.
Category and decision are stored as two independent fields.

## A2. Primary check decisions (spec 4.7)
Modeled as `PrimaryCheckDecision`: ACCEPTED, RETURNED_TO_APPLICANT, NON_APPLICABILITY_OPINION,
ROUTE_CHANGED, REJECTED. The spec does not state which categories permit which decisions, so the
MVP allows any decision for any category and records the reason.

## A3. Tariffs and price formula (spec 12.2)
Tariffs, coefficients and formulas are supplied by the client and are not in the spec. DEMO model:
`total = sum(item.basePrice * item.quantity) * modeCoefficient + additionalWorkFees`
with `modeCoefficient` = 1.0 TRADITIONAL / 1.5 EXPEDITED. Values live in `price_rule` rows seeded
by Flyway and marked `demo = true`.

## A4. Green certification contract-amount brackets (spec 8.4)
The spec fixes four brackets (<10M, 10-20M, 20-30M, >30M UZS) but explicitly leaves the treatment of
the exact boundary values 10M / 20M / 30M to the client. DEMO rule: lower bound inclusive, upper bound
exclusive (`[0,10M)`, `[10M,20M)`, `[20M,30M)`, `[30M,+inf)`). Stored as an enum on `PerformedWork`.

## A5. Deadlines (spec 5.8, 12.9)
Concrete durations are not defined. DEMO: stage deadline in business hours on `workflow_stage`,
EXPEDITED halves it (min 1 day). Payment waiting period: 10 calendar days; on expiry the case is
flagged `PAYMENT_OVERDUE` and a decision task is raised for the authorized manager - the system never
auto-rejects (spec 12.9 reserves rejection for an authorized manager).

## A6. Positions, departments, users (spec 3.10, 5.6, 16.12)
Final names come from the client. Seed data uses neutral demo departments (MAIN, LABORATORY,
GREEN_CERTIFICATION, ACCOUNTING) and demo accounts.

## A7. Workflow versioning strategy (spec 5.12, 16.11)
Each `(workflow.code, workflow.version)` pair is an immutable row with its own stages and transitions.
Publishing a new version inserts new rows and deactivates the previous one; historical rows are never
mutated. `electronic_case.workflow_id` pins the exact version, so old cases keep their original route.

## A8. Audit immutability (spec 20.3)
Enforced at three levels: no update/delete API, the application DB role holds only INSERT/SELECT on
`audit_log`, and a BEFORE UPDATE OR DELETE trigger raises an exception. Each row also stores
`prev_hash`/`row_hash` so tampering via a superuser is detectable.

## A9. Payment gating (spec 12.8)
Whether execution may start before full payment is a route-level flag
`workflow.allow_execution_before_full_payment`, default `false`.

## A10. Graphic signature (spec 7.5.5)
Out of scope for this slice. The spec itself states a graphic signature is not an electronic digital
signature unless legislation says otherwise, so no crypto is implied.

## A11. docker-compose / local-dev database credentials (Phase 1)
Not a business rule, but flagged per the same [DEMO] convention: `docker-compose.yml` and
`application-local.yaml`/`application-demo.yaml` use a fixed `crm`/`crm`/`crm` database name,
username and password so the stack is runnable with zero setup. `application-prod.yaml` takes
`DB_URL`/`DB_USERNAME`/`DB_PASSWORD` from the environment instead - never hardcode a production
credential here.

## A12. JWT secret (Phase 3, SECURITY_SPEC.md 1)
`application.yaml` sets `jwt.secret: ${JWT_SECRET:demo-only-secret-key-...}` - a hardcoded fallback
so the app boots with zero setup, exactly like A11's DB credentials. Any real deployment MUST set
`JWT_SECRET` in the environment; the fallback is intentionally labeled "demo-only" in the value
itself as a second line of defense against it leaking into a real deployment unnoticed.
SECURITY_SPEC.md already flags HS256 itself as `[DEMO]` (production note: rotate to RS256 + JWKS).

## A13. Refresh/logout have no server-side token tracking (Phase 3, SECURITY_SPEC.md 1 & 10)
`SECURITY_SPEC.md` 10 explicitly puts "refresh-token rotation and reuse detection" and rate
limiting out of scope for this slice. Consequence recorded here: `POST /auth/refresh` validates
the presented refresh token and mints a fresh access+refresh pair with no rotation bookkeeping, and
`POST /auth/logout` is a stateless `204` no-op - there is no server-side refresh-token store to
invalidate, so a client's old tokens remain cryptographically valid until they expire (15 min
access / 7 day refresh) even after "logout". A real deployment needs a revocation list (e.g. `jti`
blocklist in Redis) before this is production-safe.

## A14. Audit actor's role code when a user holds several roles (Phase 4, FINAL_DOMAIN_MODEL.md 9)
`audit_log.actor_role_code` is a single `varchar(40)`, but `app_user` can hold several roles via
`user_role`. `AuditActorResolver` records the alphabetically first role code (deterministic, fits
the column); the full role set for that user is always recoverable from `user_role` via `user_id`
if a reviewer needs it. No spec clause dictates which one to pick when there are several - this is
the MVP-reasonable choice.

## A15. `AuditAction` extended beyond FINAL_DOMAIN_MODEL.md's listed set (Phase 4)
FINAL_DOMAIN_MODEL.md 9 calls its `AuditAction` list a "minimum set, spec-driven." Phase 4's admin
CRUD needs to audit *edits* (not just creation) to `User`, `Department` and `Position`, and no
listed action covers that. Added `USER_UPDATED`, `DEPARTMENT_CHANGED`, `POSITION_CHANGED` to the
enum and to `V10__audit.sql`'s `ck_audit_action`/`ck_audit_case_scope` CHECK constraints (both are
administrative, `case_id IS NULL`, same as `USER_CREATED`/`USER_BLOCKED`). `Service`/`WorkType`/
`ExternalStage` edits reuse the existing `REFERENCE_DATA_CHANGED` action instead of getting their
own - one action per resource class was enough, differentiated by `oldValue` being null on create.

## A16. `USER_ADMIN` vs `REFERENCE_DATA` mapping for admin endpoints (Phase 4, SECURITY_SPEC.md 3)
The grant matrix has no dedicated permission section for "departments" or "positions" as such.
SECURITY_SPEC.md's own `ADMIN` role-capability row groups "users, roles, permissions, departments,
positions" together, so `/admin/users*`, `/admin/departments`, `/admin/positions` and
`/admin/roles/{code}/permissions` are all gated by `USER_ADMIN:*`. That same row separately lists
"services, routes, reference data," which maps directly to `REFERENCE_DATA:*` gating
`/admin/services`, `/admin/work-types`, `/admin/external-stages`.

## A17. Applicant self-registration mechanics (Phase 5, spec 15.2)
API_SPEC.md 2's authz for `POST /applicants` reads "`APPLICANT` (self)," which presupposes an
already-authenticated `APPLICANT` principal - circular for a brand-new person with no account yet.
No `/auth/register` endpoint exists anywhere, `CreateApplicantRequest`'s documented shape has no
password field, and `SECURITY_SPEC.md`'s public-endpoint allowlist doesn't list `/applicants`.
Confirmed as a genuine, unresolved gap (not something already answered elsewhere in `docs-2/`)
before implementing. Resolution: `POST /applicants` is public (added to `SecurityConfig`'s
permitted paths, POST only - `GET`/`PATCH /applicants/{id}` stay authenticated as documented);
`CreateApplicantRequest` gained a `password` field; `ApplicantService.create` atomically creates
the `Applicant` row and a linked `User{role=APPLICANT, applicantId, passwordHash}` in one
transaction. This is the "self-registration flow" Phase 4's admin user endpoint already refused to
substitute for.

## A18. `APPLICATION:CREATE` for staff PAPER/SINGLE_WINDOW intake is not seeded (Phase 5)
API_SPEC.md 3 says `POST /applications` allows "staff with `APPLICATION:CREATE` for
PAPER/SINGLE_WINDOW," but the seeded grant matrix (`V2__security_and_org.sql`, asserted exactly by
`SecurityGrantMatrixTest`) gives `APPLICATION:CREATE` to `APPLICANT` only - no staff role holds it
out of the box, and `SECURITY_SPEC.md`'s own printed table agrees (only `APPLICANT` gets `C` on
`APPLICATION`). Rather than picking a role to add it to - which would diverge the code from that
already-tested table - the seed is left untouched. The endpoint correctly checks for
`APPLICATION:CREATE`; a deployment that wants e.g. `OPERATOR` to intake paper applications grants
it through Phase 4's own `PATCH /admin/roles/{code}/permissions`, no code change needed.

## A19. Application number format (Phase 5)
No doc fixes a format for `application.number` (just `UNIQUE`, generated). Used
`APP-{yyyy}-{6-digit sequence}` (e.g. `APP-2026-000042`), sequence derived from the current row
count + 1. Good enough for a demo; not safe under real concurrent load (a proper sequence/lock is a
production concern, not something this slice's `TEST_MATRIX.md` rows require).

## A20. Staff application listing is unscoped for now (Phase 5, API_SPEC.md 3)
"`GET /applications` ... staff → scope-filtered" is minimally interpreted as "every application,"
for this phase: nothing before Phase 7 links an `Application` to a department, so there is nothing
to scope by yet. Real scoping (department, case assignment) becomes possible once
`ElectronicCase`/`CaseStage` exist and `register` starts populating them.

## A21. Phase-6-scoped demo seed, trimmed from the full reference file (Phase 6)
`docs-2/FLYWAY_MIGRATIONS/demo/V900__demo_seed_data.sql` already contains a full authored seed
(departments, positions, users, applicants, workflow v1/v2, price rules) - but its user password
hashes are literal placeholder text (`$2a$10$DEMOhashDEMOhash...`, not real bcrypt), confirming it
is a structural sketch for Phase 13 to finish, not something to copy verbatim this early.
`src/main/resources/db/demo/V900__demo_seed_data.sql` was authored now containing only what
workflow v1/v2 need to exist and be testable: 4 departments (`MAIN`, `LABORATORY`, `ACCOUNTING`,
`GREEN_CERTIFICATION`), the `WASTE_CERTIFICATION` service, and workflow v1 (`ACTIVE`) + v2
(`DRAFT`). `published_at`/`published_by` on the seeded v1 use `now()`/`NULL` respectively - no
admin user exists yet in this phase's seed to attribute the publish to. Wired into
`local`/`demo`/`test` Flyway locations only, never `prod`. Phase 13 extends this same file with
users/applicants/price-rules later - it should not be rewritten from scratch.

## A22. Workflow v2's stages have no transitions yet (Phase 6)
The seeded `WASTE_CERT_ROUTE` v2 (`DRAFT`) has 9 stages but zero `workflow_transition` rows, same
as the reference seed file. This is intentional, not an oversight: v2 stays a `DRAFT` demonstrating
copy-on-write's "old case keeps its old route" guarantee is testable in principle, but nothing in
Phase 6's own DoD (W-12, C-07) requires v2 to actually be publishable. A later phase's test (W-11:
"register a case on v1, publish v2 that deletes a stage, drive the old case to completion") is what
will need v2's transitions authored - at that point they should be added to this same seed file.

## A23. (number reserved, not used)
Skipped during Phase 7 numbering. Left as a gap rather than renumbering everything after it, since
several Phase 7 classes already cite A24-A34 by number in their javadoc.

## A24. `CaseLifecycleResolver` leaves two stage types' case status untouched (Phase 7, spec 4.2)
`ENDORSEMENT` and `NON_APPLICABILITY_OPINION` map to `null` ("keep the current status") rather than
to a new `CaseStatus` value. `ck_case_status` has no `ENDORSEMENT` entry - endorsement runs on a
document version, not on the case, and forcing the case to `ON_SIGNING` before anyone has signed
would misreport it in every list. For `NON_APPLICABILITY_OPINION`, the status that matters was
already set by the primary-check decision that routed the case there. [DEMO]

## A25. Engine gates for unimplemented blocks default to "not satisfied" (Phase 7)
Three `ConditionHandler`/`StageActivator` seams point at phases that do not exist yet:
`PRIMARY_CHECK`/`EXECUTION`/`FINAL_REVIEW` create no `Task` (Phase 9), `ENDORSEMENT`/`SIGNING` open
no `ApprovalRound`/promote no document version (Phase 10), and `PAYMENT_STATE_SATISFIED` /
`APPROVAL_ROUND_COMPLETED` have no `PaymentStateProvider`/`ApprovalStateProvider` bean to ask. Each
of these resolves to the safe direction - the stage still activates and gets its deadline, but the
gate stays shut (`PaymentState.unpaid()`, `isRoundCompleted() == false`) and the route waits rather
than being waved through by a missing module. This is an honest `[~]`, not a fake pass. [DEMO]

## A26. `CaseAccessPolicy`'s task-based view clauses fall back to department scope (Phase 7,
SECURITY_SPEC.md 5)
SECURITY_SPEC.md 5's `canViewCase` for SPECIALIST includes an `exists task(...)` clause ("assigned
to a task on this case"), but `task` does not exist until Phase 9. Until then, SPECIALIST and
DEPARTMENT_HEAD both fall back to the department-based clause (their department is the case's main
or a participating department) - narrower than "any case in the system" and wider than the final
rule. Phase 9 tightens this by adding the task lookup in `CaseAccessPolicy` alone; nothing else
should need to change. Same gap noted in `ElectronicCaseRepository#search`'s department filter and
`CaseService#performPrimaryCheck`'s "assigned task" authorization line from API_SPEC.md 4. [DEMO]

## A27. `PrimaryCheckEvaluator` checklist keys and the ungraded case (Phase 7, spec 1.5)
The specification names the three primary-check categories (RED/YELLOW/GREEN, A1) but not the shape
of the checklist a specialist submits. `PrimaryCheckRequest.checklist` is read for four keys:
`missingRequiredFields`, `missingRequiredDocuments`, `missingOptionalData` (each a collection or a
comma-separated string) and `applicantDataInconsistent` (a boolean). A1 also leaves one combination
undefined: a missing required *field* (as opposed to a document) belongs to no stated bucket: this
is graded RED here, on the reasoning that YELLOW is reserved for data the case can proceed without,
and a missing mandatory field is not that. [DEMO]

## A28. Finance/document/notification fields stay null through Phase 7 (Phase 7, API_SPEC.md 4)
`CaseResponse.finance` and `ApplicantTrackingResponse`'s `contract`, `payment`, `finalDocument` and
`notifications` are shaped now but populated `null`/empty until Phase 8 (accounting), Phase 10
(documents) and Phase 12 (notifications) exist. A truthful `null` was chosen over a placeholder zero
or empty string, which would read as "you owe nothing" instead of "not calculated yet" - a
meaningful difference for a certification-body client checking their balance. The applicant-facing
correction due-date is also fixed to UTC rather than the server's default zone, so the same case
renders the same calendar date in every environment; a real per-applicant timezone is a portal
concern, out of scope here. [DEMO]

## A29. Registration picks the first ACTIVE workflow, ordered by code, when a service has several
(Phase 7, spec 5.12)
`uq_workflow_one_active` is unique per `(code)`, not per `(service_id)`, so nothing in the schema
stops two different route families from both being ACTIVE for the same service at once.
`WorkflowRepository#findByServiceIdAndStatusOrderByCodeAsc` returns every match and
`CaseService#register` takes the first, ordered by workflow code, which is deterministic but
arbitrary - a real deployment would need an explicit service-to-route mapping (or a routing rule) to
choose between them instead of alphabetical luck. Demo/test data should keep exactly one ACTIVE
workflow per service until such a mapping exists, to avoid registering onto the wrong route by
accident. [DEMO]

## A30. `APPLICATION:EDIT` and `CASE:EDIT` are granted to no role in the seeded matrix (Phase 7,
SECURITY_SPEC.md 3)
`POST /applications/{id}/register` requires `APPLICATION:EDIT` and `PUT /cases/{id}/items` requires
`CASE:EDIT` (API_SPEC.md 3-4), but SECURITY_SPEC.md 3's table (row-for-row identical to the seeded
`V2` matrix, per A16/A18's precedent of not diverging from the tested grant table) gives neither
permission to any of the seven roles. Registration and item editing are therefore reachable today
only by a deployment granting one of these to a role via Phase 4's admin endpoint - exactly as A18
already established for staff `APPLICATION:CREATE`. Integration tests grant them temporarily to
`DEPARTMENT_HEAD` and revoke them in `@AfterEach`, so `SecurityGrantMatrixTest`'s exact-match
assertion of the seeded table is never left dirty by another test class. [DEMO]

## A31. `REQUIRED_DATA_MISSING` checks "any data at all", not "this route's required fields" (Phase 7,
spec 15.3)
API_SPEC.md 3's `422 REQUIRED_DATA_MISSING` is specified as "route-mandatory fields or documents are
absent", but nothing in the schema declares which fields a given `workflow_stage` mandates, and
documents are Phase 10's table. `CaseService#requireRegistrationData` narrows this to what is
expressible today: an application whose `formData` carries neither a single field (other than the
always-present `items` key) nor a single item cannot be registered. Widening this to a real
per-route required-field/document configuration is a change to this one private method, not to the
endpoint or its transaction boundary. [DEMO]

## A32. Internal case comments are not audited (Phase 7, spec 13.5.1/17.8)
`AuditAction` mirrors `ck_audit_action` (V10) value for value, so inventing a `COMMENT_ADDED`
constant in `CaseService#addComment` would insert a row the database CHECK constraint rejects; the
only ways to audit comments today are "no audit" or a migration outside Phase 7's declared scope.
No audit row is written for `POST /cases/{id}/comments`. If comment auditing is later required, add
the enum value to `V10`'s `ck_audit_action`/`ck_audit_case_scope` CHECK constraints in the same
migration-editing style Phase 4's A15 already used, not by working around the constraint. [DEMO]

## A33. Case items are locked by case lifecycle, not by a real contract row (Phase 7, API_SPEC.md 4)
Case items are specified as editable "only before price confirmation", but the `Contract` row that
would prove confirmation happened is Phase 8's table. `CaseService#requireItemsEditable` locks item
edits based on the case's `CaseStatus` instead (`WAITING_PAYMENT`, `IN_EXECUTION`, `FINAL_REVIEW`,
`ON_SIGNING`, `COMPLETED`, `REJECTED`) - a case already past accounting, rejected or completed does
not accept a new item composition. This is a narrower, earlier-arriving proxy for the real rule;
Phase 8 should tighten it to the actual price-confirmation event once `Contract` exists, rather than
leaving both checks active side by side. [DEMO]

## A34. The specialist's primary-check category is never overridden by the system (Phase 7, spec 4.6)
`PrimaryCheckEvaluator.categoryFrom(checklist)` can grade a checklist differently from the category
the specialist actually submitted (A2 permits any category/decision pairing), and `CaseService`
records both: the specialist's `category` is what persists as authoritative on `PrimaryCheck` and
`ElectronicCase`, while the system's own grading is written into the same `PRIMARY_CHECK_COMPLETED`
audit row under `derivedCategory`. The system does not overrule the human, on the reasoning that
spec 4.6 gives the specialist the judgment call - but a divergence between the two is now visible to
a reviewer reading the audit trail instead of silently lost. [DEMO]

## A35. Overdue-payment task creation is a port with no implementation until Phase 9 (Phase 8, spec 12.9)
`PaymentWaitingScheduler` flags `payment.overdue`/`electronic_case.payment_overdue` and audits
`PAYMENT_OVERDUE` on its own - that part is fully real. FINAL_IMPLEMENTATION_ORDER.md's Phase 8 line
also says the scheduler "creates a manager decision task", but `Task` does not exist until Phase 9.
Following Phase 7's exact precedent for the same situation (A25 - `ApprovalStateProvider`,
`PaymentStateProvider`), the scheduler depends on `finance.port.OverduePaymentTaskCreator` via an
`ObjectProvider` and silently skips task creation when no bean is present; nothing observable is
lost except the follow-up task itself, which Phase 9 supplies by implementing that one interface. A
route the system NEVER auto-rejects on this (spec 12.9 explicitly reserves that decision for an
authorized manager via `AccountingService#setPaymentStatus`) - the scheduler has no method that
could do so even if it wanted to. [DEMO]

## A36. Mode change AFTER price confirmation is blocked, not silently recalculated (Phase 8, spec 12.3)
FIX 9 / spec 12.3 only specifies automatic recalculation for a mode change BEFORE confirmation
(`AccountingService#setProcessingMode` supersedes the live `ACTIVE` `PriceCalculation` and inserts a
`MODE_CHANGED` one). What happens if the mode changes AFTER the price was already `CONFIRMED` - and
possibly after a `Contract` already exists - is not specified. DEMO choice: the mode still changes
on the case (accounting may legitimately need to record a corrected mode even late), but no
automatic recalculation happens once a calculation is `CONFIRMED` - a fresh
`POST /accounting/cases/{id}/price/calculate` call is required to produce a new one, which the
accountant can then choose to `confirm` again. Silently recalculating a number that may already be
on a signed contract was judged riskier than requiring an explicit follow-up call. [DEMO]

## A37. PRIMARY_CHECK creates no task; EXECUTION and FINAL_REVIEW both do (Phase 9, spec 7.3/7.4)
`StageActivatorRegistry`'s own javadoc (Phase 7) already names this decision but the entry recording
it here was never actually written - closing that gap now. `PRIMARY_CHECK` does not get a
`StageActivator`/`Task`: Phase 7 already implemented that step's whole flow via
`POST /cases/{id}/primary-check`, which needs no task record - the specialist acts directly on the
case. `EXECUTION` (`ExecutionStageActivator`) and `FINAL_REVIEW` (`FinalReviewStageActivator`) both
create a `Task` via `TaskFactory` on activation, because both require a specialist to produce and
submit a result before the stage can complete (spec 7.5.7's `requires_result` principle). An earlier
draft of this phase also wrote a `PrimaryCheckStageActivator` that created a task for PRIMARY_CHECK
too - contradicting this exact entry's own reasoning and producing a permanently-orphaned task on
every single case registration (nothing in the real flow ever assigns/starts/completes it). Removed;
if primary-check ever needs a task record, that decision belongs in this file, not as a silent
addition alongside code that says the opposite. [DEMO]

## A38. `OverduePaymentTaskCreatorImpl`'s task is idempotent per PAYMENT_CONTROL stage (Phase 9, spec 12.9)
If `PaymentWaitingScheduler` sweeps twice for the same case before a manager acts (e.g. two
`@Scheduled` runs before anyone views the task), `OverduePaymentTaskCreatorImpl.createDecisionTask`
checks `TaskRepository.findByCaseStageId` first and is a no-op on the second call - the existing
task IS the decision task, uq_task_case_stage would reject a second row for the same stage anyway.
No separate "already flagged" tracking needed beyond that lookup. [DEMO]

## A39. `CaseAccessPolicy`'s SPECIALIST view rule keeps the department-touch fallback, wider than
SECURITY_SPEC.md 5's bare pseudocode (Phase 9, spec 3.4/4.5)
SECURITY_SPEC.md 5's pseudocode for SPECIALIST is task-existence only (`exists task(assignedUser)
OR exists task(assignedDepartment)`), with no department-touch clause at all - unlike
DEPARTMENT_HEAD's rule, which keeps one. Implemented literally, that blocks a specialist from the
primary-check step itself: `PRIMARY_CHECK` creates no task (A37), and no `EXECUTION`/`FINAL_REVIEW`
task exists yet either at that point in a case's life, so a specialist in the case's own department
would get `404` (object-level policy misread as "not this applicant's case", except staff get 403 -
see `CaseAccessPolicy#requireCanView`'s two-failure-mode note) trying to perform the one action
spec 3.4/4.5 assigns to their role. Confirmed as a real regression by running
`CaseLifecycleIntegrationTest` (Phase 7, unrelated to this phase) against the literal pseudocode -
6 of 10 tests failed with 403 on the primary-check call. Fix: SPECIALIST keeps the department-touch
fallback (same as DEPARTMENT_HEAD) ALONGSIDE the two new task-based clauses, rather than replacing
it - wider than the spec's bare form, but the bare form itself regressed a working, spec-mandated
flow that was never exercised against it before this fix. [DEMO]

## A40. `ApprovalTask.sequenceNo` is stored but SEQUENTIAL ordering is not enforced (Phase 10, spec 13.3)
`ApprovalRound.mode` (SEQUENTIAL/PARALLEL) and `ApprovalTask.sequenceNo` are both persisted and
returned in every response, but `ApprovalService.approve`/`reject` let ANY participant decide their
task at any time regardless of the round's mode or their own sequence position - a SEQUENTIAL round
behaves identically to a PARALLEL one today. None of Phase 10's named DoD rows (D-01…D-08, S-10)
test sequential gating specifically, so this was not caught by the required test suite, but it is a
real gap against spec 13.3's "участники согласовывают по очереди" for SEQUENTIAL rounds. Left
undone rather than half-implemented under this session's time budget - a real implementation would
need `approve`/`reject` to check that every LOWER `sequenceNo` required task in the same round is
already decided before accepting this one's decision, mirroring the parallel gate's
`ALL_REQUIRED_PARALLEL_TASKS_DONE` pattern but ordered instead of set-based. [DEMO]

## A41. Document/Approval entities use JPA `@ManyToOne` associations, not raw UUID foreign keys
(Phase 10)
Every other phase in this codebase deliberately uses raw UUID columns for foreign keys instead of
JPA relations - see `ElectronicCase`'s own javadoc: "`spring.jpa.open-in-view = false` makes lazy
proxies a liability, and the service layer loads what it needs explicitly." Phase 10's `Document`,
`DocumentVersion`, `ApprovalRound`, `ApprovalTask` all use `@ManyToOne(fetch = LAZY)` instead
(`Document.electronicCase`, `Document.task`, `DocumentVersion.document`, `ApprovalRound.documentVersion`,
`ApprovalRound.electronicCase`, `ApprovalTask.approvalRound`). This did not surface as a bug in this
phase's own test suite because every access happens to stay inside the owning `@Transactional`
method (mapping happens before the transaction closes), so no `LazyInitializationException` fires -
but it is a real inconsistency with the rest of the codebase and a latent risk if a future change
calls a getter on one of these associations outside a transaction. Left as-is rather than refactored
under this session's time budget (converting all four entities to raw UUID + explicit repository
lookups, matching `CaseService`/`AccountingService`/`TaskService`'s pattern, would touch every method
in `DocumentService`/`ApprovalService`/`SigningService`); flagged here so a later phase doesn't
copy the pattern forward. [DEMO]

## A42. `CreateDocumentRequest.taskId` links a document to the task it formalises (Phase 11, spec 8.3)
API_SPEC.md 7's `CreateDocumentRequest{documentType,title}` has no `taskId` field, and nothing in
Phase 10's `DocumentService` ever set `document.task_id` - meaning Phase 7's schema comment ("the
deliberate linkage" `document.task_id -> task.id`) had no code path that could ever populate it.
This broke Phase 11's own `PerformedWorkRecorder`, which resolves a stage's "supporting document"
(spec 8.3) by looking up the `Document` whose `taskId` matches the completing stage's `Task` - a
lookup that could never find anything. Added an optional `taskId` to `CreateDocumentRequest`;
`DocumentService.createDocument` sets `document.task` when provided. A real deployment would
likely infer this automatically from the caller's context (documents are usually created by
whoever is doing the task) rather than requiring the client to pass it explicitly - narrowed to
the explicit field for this slice rather than guessing at an inference rule with no spec backing. [DEMO]

## A43. `PerformedWork.processingMode` defaults to TRADITIONAL when the case's mode is still unset
(Phase 11, spec 8.1)
`performed_work.processing_mode` is `NOT NULL`, but `electronic_case.processing_mode` stays `NULL`
until an accountant explicitly calls `POST /accounting/cases/{id}/processing-mode` - nothing sets
it implicitly, not even `AccountingService.calculatePrice` (which defaults to TRADITIONAL for the
calculation itself but never persists that choice onto the case). A stage can complete, and
therefore this recorder can fire, before that call ever happens - confirmed as a real, reproducible
`23502 null value in column "processing_mode"` failure while testing PW-01, not a theoretical edge
case. `PerformedWorkRecorder` now falls back to `ProcessingMode.TRADITIONAL` when the case's own
mode is null, matching the same "unset means TRADITIONAL" convention `DeadlineCalculator` already
uses elsewhere in this codebase. [DEMO]

## A44. Testing A-05's "forced tamper" requires temporarily disabling `tr_audit_log_immutable`
(Phase 11, spec 20.3)
`audit_log` rejects UPDATE/DELETE/TRUNCATE unconditionally, even for the schema owner (A8) - so
there is no way to "force a tamper" for A-05's test without briefly disabling the guard trigger
itself (`ALTER TABLE audit_log DISABLE TRIGGER tr_audit_log_immutable`), performing the UPDATE,
then re-enabling it (in a `finally` block, so a failed assertion never leaves the table
unprotected for the rest of a shared-container test run). This is test-only scaffolding to prove
`verify_audit_chain()` actually detects a mismatch - it is not a code path the application itself
ever exercises, and it does not weaken the production guarantee (the trigger is re-enabled before
the test method returns either way). [DEMO]

## A45. `IdempotencyFilter` stores only successful (2xx) responses; keyed by SHA-256 of method+URI+body
(Phase 12, plan §19)
V11's `command_log` contract says a replay with the same key returns the stored response. A first
call that fails validation (4xx) is NOT stored, so a client that fixed its mistake and retried with
the SAME key can still succeed - storing a failure would permanently poison that key. The request
hash covers method + URI + raw body bytes (not headers), computed via a custom
`CachedBodyHttpServletRequest` (Spring's own `ContentCachingRequestWrapper` cannot be read by the
filter AND by the controller afterwards - reading it once exhausts the underlying stream for the
other reader, a well-known gotcha). Only `POST`/`PUT`/`PATCH` requests carrying the header are
affected; every other request is untouched. [DEMO]

## A46. `DataIntegrityViolationException` -> business code mapping covers `uq_*` only, not `ck_*`
(Phase 12)
`ConstraintViolationTranslator` maps every named UNIQUE constraint/partial index reachable via a
genuine concurrent race (per WORKFLOW_ENGINE_DESIGN.md 12's own table) to a specific business code.
CHECK constraints are deliberately left unmapped, falling through to the generic
`DATA_INTEGRITY_VIOLATION` response: a CHECK failing means Bean Validation and the service layer's
own pre-checks already should have rejected the request - it is a genuine application bug if one
ever fires, not a race, so smoothing it into a specific-sounding business code would be misleading
rather than helpful. [DEMO]

## A47. A genuine PostgreSQL deadlock is mapped to the same `409 CONCURRENT_MODIFICATION` as an
optimistic-lock loss (Phase 12, C-01/C-02)
Writing genuinely concurrent tests (`ExecutorService` + `CountDownLatch`, not sequential replays)
surfaced two real, previously-undetected deadlocks under simultaneous requests:
1. `CaseStageRepository`'s old `lockRequiredSiblings` locked the parallel-group siblings via a
   single `SELECT ... FOR UPDATE ... ORDER BY id` - which does NOT guarantee PostgreSQL acquires
   the row locks in that order (the `ORDER BY` only reorders the result set; rows are locked as
   the underlying scan visits them). Fixed by locking one row at a time, in ascending `id` order,
   via N separate `SELECT ... FOR UPDATE WHERE id = ?` statements (a `default` method on the
   repository interface) - every transaction now requests the same lock sequence.
2. `WorkflowEngine.completeStage()` mutated the completing stage's OWN row (and flushed it via the
   audit write) BEFORE calling `advance()` -> `lockRequiredSiblings()` for the group. Two siblings
   completing at the same instant would each hold an exclusive lock on their OWN row first, then
   deadlock reaching for each other's. Fixed by acquiring the WHOLE group's lock (own row
   included) up front, before any row in the group is touched, so every transaction requests the
   same lock sequence from the very start.
Even with both fixes, a residual, lower-probability deadlock class can still occur (confirmed via
C-01 in the full suite) - PostgreSQL's `CannotAcquireLockException`/`PessimisticLockingFailureException`
is now mapped to the same `409 CONCURRENT_MODIFICATION` response as `ObjectOptimisticLockingFailureException`,
since a deadlock and a lost optimistic-lock race mean the same thing to a client: reload and retry.
This is a defensive, general-purpose hardening fix (protects every endpoint against this class of
race, not only the ones this phase happened to test), not a workaround for one specific test. [DEMO]

## A48. `audit_log_chain()`'s hash-chain computation is now serialized with a transaction-scoped
advisory lock (V12, Phase 12)
The original trigger (V10) read "the last row" via a plain `SELECT row_hash ... ORDER BY seq DESC
LIMIT 1` with no locking. Two genuinely concurrent transactions both writing audit rows (first
exercised by this phase's C-01/C-02 tests - no earlier phase ever had two truly simultaneous
requests) can both read the same predecessor under READ COMMITTED before either commits, so both
compute their hash against it; `verify_audit_chain()` then reports a false break on the row that
ends up second by `seq`, even though nothing was tampered with. V12 adds
`PERFORM pg_advisory_xact_lock(hashtext('audit_log_chain'))` as the trigger's first statement,
serializing chain-tip computation across ALL concurrent audit_log inserts, verified against a
standalone two-connection reproduction outside the app. This closes the race for two audit writes
that do not share a business transaction; two audit writes THAT DO share one (e.g. a stage's own
completion event followed later by its sibling-group evaluation) are still expected to interleave
with a concurrent sibling's writes mid-transaction - each one is still individually serialized by
the same lock at the moment it is written, so the chain itself stays internally consistent.
`PerformedWorkAndAuditIntegrationTest`'s A-05 test was also narrowed to assert intact/broken
against its OWN fixture's row range rather than the whole shared `audit_log` table, since that
table is shared across the entire `mvn verify` run (`AbstractIntegrationTest`'s one container) and
an unrelated test's own genuine race is not this test's concern. [DEMO]

## A49. `CaseApprovalStateProvider.isRoundCompleted` ignores its `workflowStageId` parameter
(Phase 13, spec 13.7)
The port signature is `isRoundCompleted(caseId, workflowStageId)`, matching
`PaymentStateProvider`'s shape, but `ApprovalRound` has no `workflow_stage_id` column - only
`case_id` and `document_version_id`. Since a case only ever has one ENDORSEMENT stage open at a
time in this slice's routes (no parallel endorsement stages), "does this case have any
`COMPLETED_APPROVED` round" is unambiguous without correlating to the specific stage row. A route
with two concurrently-open endorsement stages would need this port's contract widened (add a
`workflow_stage_id` column to `approval_round`, or infer it from the document's produced-by
stage) - out of scope for this slice's routes. [DEMO]

## A50. Demo bootstrap grants `APPLICATION:EDIT`/`CASE:EDIT` to `DEPARTMENT_HEAD` via the admin
API, not via seed data (Phase 13, see A30)
A30 established that no role holds these permissions in the seeded matrix, by design (matching
the spec's own table exactly). Rather than special-casing `db/demo/V900` to grant them directly in
SQL - which would make the demo's grant matrix silently diverge from `SECURITY_SPEC.md` §3 and
from what `SecurityGrantMatrixTest` asserts - the demo script's step 0 is the one API call a real
deployment needs anyway: an admin uses `PATCH /api/admin/roles/DEPARTMENT_HEAD/permissions` once,
after first boot. `DemoScenarioWalkthroughTest` performs and then reverts this same call so the
shared test container's grant matrix is unaffected for every other test class. [DEMO]
