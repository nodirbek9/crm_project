# MASTER PROMPT v2 (corrected)

Paste into the coding agent at the backend project root. This supersedes section 4 of the original
MASTER_PLAN. Deltas from v1 are marked `[FIX n]` and justified in `docs/PLAN_REVIEW.md`.

```text
You are my Senior Solution Architect, Senior Java Engineer, Database Architect,
Security Engineer and QA Engineer.

Build a production-minded, interview-quality BACKEND vertical slice for an IT Hunter
technical assignment based on the attached CRM specification. Not the whole enterprise CRM.
The backend must be fully runnable, testable and explainable. Frontend is OUT OF SCOPE.

Read docs/PLAN_REVIEW.md and docs/ASSUMPTIONS.md first. They already contain the gap analysis
against the specification. Do not redo that analysis.

SCOPE (end-to-end flow):
Application -> ElectronicCase -> Primary Check (category + decision) -> Route selection
-> Accounting (processing mode, preliminary price, price confirmation) -> Contract -> Payment
confirmation -> Execution block (sequential + parallel tasks) -> Task results with versioning
-> Document + DocumentVersion -> Multi-participant Approval -> Final review -> Signing
-> Completed -> Audit log + Performed-works accounting.

TECHNOLOGY
Java 21, Spring Boot 3.x, Spring Web, Spring Data JPA, Spring Security, JWT, PostgreSQL,
Flyway, Bean Validation, MapStruct, JUnit 5, Mockito, Testcontainers, OpenAPI, Docker Compose.
MODULAR MONOLITH. No microservices.
Modules: auth, user, applicant, application, case, workflow, document, task, approval, payment,
work (performed-works accounting), audit, admin.

DOMAIN MODEL
User, Role, Permission, Position, Department, Applicant, Service, Application, ElectronicCase,
CaseItem, Workflow, WorkflowStage, WorkflowTransition, ExternalStage, Task, TaskResult,
Document, DocumentVersion, ApprovalRound, ApprovalParticipant, PriceRule, PriceCalculation,
Contract, Payment, PerformedWork, CaseComment, AuditLog.

[FIX 1] Document and DocumentVersion are MANDATORY, not optional. Approval targets a specific
document VERSION. A reviewed version is never overwritten; corrections create a new version and
preserve the previous one, its remarks and its review history (spec 6.6, 13.4, 13.5).

[FIX 2] Roles: ADMIN, APPLICANT, ACCOUNTANT, HEAD_OF_CERTIFICATION_BODY, DEPARTMENT_HEAD,
SPECIALIST, OPERATOR. Do NOT merge the two manager roles: only HEAD_OF_CERTIFICATION_BODY may
SIGN final documents; only DEPARTMENT_HEAD assigns and reassigns specialists inside its department
and approves their results (spec 3.2, 3.3, 5.5, 7.3).

[FIX 3] Permissions are exactly the seven actions of spec 16.4: VIEW, CREATE, EDIT, ENDORSE
(soglasovanie), APPROVE (utverzhdenie), SIGN (podpisanie), BLOCK. Seed them; do not invent others.
Role, Position and Department are three independent attributes; changing position must not change
permissions (spec 16.13). ADMIN gets no expert, financial or signing permission (spec 16.17).
Any read of confidential financial data by ADMIN raises CONFIDENTIAL_DATA_ACCESSED (spec 16.18).

[FIX 4] Approval is multi-participant. ApprovalRound(documentVersionId, mode SEQUENTIAL|PARALLEL)
with one ApprovalParticipant row per participant (status SENT/IN_REVIEW/APPROVED/REJECTED, required
flag, order, comment). REJECTED requires a comment. The round completes only when every REQUIRED
participant approved. An applicant can be a participant (spec 13.2, 13.3, 13.6, 7.11).

[FIX 5] AuditLog.caseId is NULLABLE - administrative events (user, role, permission, route,
template, reference-data and reporting-access changes with old/new values) have no case (spec 16.10,
18.12). Immutability is enforced in the DATABASE, not only by omitting endpoints: the application DB
role holds INSERT+SELECT only on audit_log, plus a BEFORE UPDATE OR DELETE trigger that raises, plus
prev_hash/row_hash chaining (spec 20.3).

[FIX 6] External stages are N:1. Several internal stages map to ONE ExternalStage shown to the
applicant (spec 5.11, 15.6). Do not use a plain externalName string on the stage.

[FIX 7] CaseStatus is a lifecycle, not a stage list: REGISTERED, PRIMARY_CHECK_DONE, IN_ACCOUNTING,
WAITING_PAYMENT, IN_EXECUTION, FINAL_REVIEW, ON_SIGNING, COMPLETED, RETURNED, REJECTED. Current
position in the route lives in current_stage_id. Category, decision, route, mode and status are five
independent fields on ElectronicCase (spec 1.5, 4.6).

[FIX 8] Primary check stores TWO things: PrimaryCheckCategory (RED/YELLOW/GREEN) and
PrimaryCheckDecision (ACCEPTED, RETURNED_TO_APPLICANT, NON_APPLICABILITY_OPINION, ROUTE_CHANGED,
REJECTED) per spec 4.7. Criteria are DEMO values - see ASSUMPTIONS A1/A2.

[FIX 9] Pricing stores the line composition behind the contract sum: CaseItem rows plus a
PriceCalculation snapshot (spec 12.6). Changing the processing mode BEFORE price confirmation
triggers automatic recalculation (spec 12.3). Confirming or overriding the price preserves the
original calculated value, the actual value, changedBy and changedAt (spec 12.4).

[FIX 10] Payment: statuses WAITING_PAYMENT, PAID, PARTIALLY_PAID, DEBT, NOT_CONFIRMED with debt
amount. Configurable payment waiting period per route; on expiry flag the case and raise a decision
task for the authorized manager - never auto-reject (spec 12.9). Route flag
allow_execution_before_full_payment controls early execution (spec 12.8). CRM never processes money;
accounting only confirms state. Store invoice reference fields on Contract (spec 12.10).

[FIX 11] Implement spec section 8, the performed-works matrix: PerformedWork(caseId, serviceType,
workType, stageId, departmentId, executorId, performedAt, processingMode, supportingDocumentId,
contractAmountBracket). One real work is recorded ONCE - unique constraint (case_id, work_type,
stage_id); returning a result to revision does NOT create another record; lab work inside expedited
certification is not duplicated as a separate urgent lab service (spec 8.5). Bracket rule is a DEMO
assumption (A4).

[FIX 12] Applicant is INDIVIDUAL or LEGAL_ENTITY with different required fields, enforced with Bean
Validation groups (spec 15.2). Application carries submissionChannel (PERSONAL_CABINET, SINGLE_WINDOW,
OTHER_SERVICE, PAPER); PAPER submissions are registered by an authorized employee, not the applicant
(spec 1.3, 5.2).

[FIX 13] Add CaseComment: internal working comments on a case or on a specific document version,
with author, department and timestamp. Distinct from approval remarks, never shown to the applicant
(spec 13.5.1, 17.4, 17.8).

WORKFLOW
Stored in PostgreSQL and data-driven. No giant if/else chain. Each (code, version) is an immutable
row set; a case pins its version so configuration changes never rewrite history (spec 5.12, 16.11).
Parallel groups: a dependent stage activates only after all REQUIRED parallel tasks complete.
Stage activation and task completion are IDEMPOTENT; use @Version optimistic locking.

TRANSACTIONS
@Transactional on registerApplication, completePrimaryCheck, assignRoute, setProcessingMode,
calculatePrice, confirmPrice, recordContract, confirmPayment, assignTask, completeTask,
returnTaskToRevision, createDocumentVersion, sendForApproval, decideApproval, sign, advanceWorkflow.
Document why each boundary exists.

API / QUALITY
Thin controllers, DTOs only (never expose entities), centralized exception handler, consistent error
body, domain exceptions for invalid transitions / forbidden / not found, Swagger with auth and error
responses. Business logic lives in application services, not controllers or repositories.

DATABASE
Flyway. FK, unique and not-null constraints, UUID PKs, timestamps, and indexes on
applications(applicant_id), cases(status), cases(current_stage_id), tasks(case_id),
tasks(assigned_user_id, status), audit_logs(case_id, created_at), performed_works(case_id).

TESTS (Testcontainers PostgreSQL for DB behavior). At minimum these 25:
1 registration creates a case; 2 category assigned; 3 decision recorded independently of category;
4 route selection; 5 applicant cannot set processing mode; 6 accountant can; 7 expedited rejected when
route forbids it; 8 price calculated with line composition; 9 mode change before confirmation
recalculates; 10 price override preserves the original calculation; 11 payment confirmation advances an
eligible case; 12 early execution blocked unless the route allows it; 13 task assignment by department
head only; 14 task cannot complete without a required result; 15 completed result not silently
overwritten; 16 revision creates a new version; 17 one incomplete required parallel task blocks the next
stage; 18 all required parallel tasks activate it; 19 duplicate completion does not advance twice;
20 rejected approval requires a comment; 21 round completes only after all required participants approve;
22 applicant cannot read another applicant's case; 23 unauthorized specialist cannot read an unrelated
task; 24 audit row cannot be updated or deleted; 25 an old workflow version still drives an old case.
Plus: revision does not create a second PerformedWork; applicant sees only the mapped external stage.

DEMO DATA
Users: admin@, head@ (certification body), depthead.main@, depthead.lab@, accountant@,
specialist1@, specialist2@, operator@, applicant@ (example.com). Service: WASTE_CERTIFICATION.
One workflow v1 with primary check -> accounting -> payment -> IDENTIFICATION -> parallel
{LABORATORY, EXPERT_REVIEW, AUDIT} -> FINAL_REVIEW -> APPROVAL -> SIGNING -> COMPLETED.
Also publish workflow v2 in seed data so test 25 is real. Reproducible.

DOCKER / README
docker-compose up must start postgres + backend with no manual DB prep. README covers problem, scope,
architecture, domain model, workflow, security, how to run, test users, demo scenario, API, testing,
assumptions, out of scope, future extensions. State plainly that tariffs, RED/YELLOW/GREEN criteria,
deadlines, contract-amount boundary rules and document requirements are DEMO assumptions.

EXECUTION RULES
Work through the phases in docs/IMPLEMENTATION_STATUS.md. After every phase: run tests, run build,
fix real failures, update IMPLEMENTATION_STATUS.md, do not break working modules. Do not ask
"should I continue" - continue automatically. If a requirement is ambiguous: check the spec, then pick
a reasonable MVP assumption, append it to docs/ASSUMPTIONS.md and continue. Never present a demo
assumption as an official client rule. Never claim a requirement is implemented when it is not.
When finished, act as an independent Senior Reviewer, produce docs/FINAL_REVIEW.md with issues
classified CRITICAL/HIGH/MEDIUM/LOW, fix all CRITICAL and HIGH, and rerun the full suite.

Start with Phase 1.
```
