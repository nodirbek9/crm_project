# FINAL_DOMAIN_MODEL.md
**IT Hunter CRM — backend vertical slice**
Source of truth: original CRM specification (sections 1–20 + performed-works matrix).
Corrections applied from `PLAN_REVIEW.md`. Demo values live in `ASSUMPTIONS.md` and are marked **[DEMO]**.

> Notation: `1..1` mandatory single, `0..1` optional single, `1..*` mandatory many, `0..*` optional many.
> **Owner** = the aggregate root that controls the lifecycle of the entity (cascade + transactional boundary).
> No entity is exposed over REST directly; every read goes through a DTO.

---

## 0. Aggregate map

| Aggregate root | Members (owned) | Referenced (by id only) |
|---|---|---|
| `Department` | — | `Position`, `User` |
| `User` | — | `Role`, `Department`, `Position`, `Applicant` |
| `Role` | `RolePermission` | `Permission` |
| `Applicant` | — | `User` |
| `Service` | — | `Workflow` (active version) |
| `Workflow` (version) | `WorkflowStage`, `WorkflowTransition` | `ExternalStage`, `Department`, `Role` |
| `Application` | — | `Applicant`, `Service`, `User` (registrar) |
| `ElectronicCase` | `PrimaryCheck`, `CaseItem`, `CaseStage`, `CaseComment` | `Application`, `Workflow`, `WorkflowStage`, `Department` |
| `Task` | `TaskResult` | `ElectronicCase`, `CaseStage`, `Department`, `User` |
| `Document` | `DocumentVersion` | `ElectronicCase`, `Task` |
| `ApprovalRound` | `ApprovalTask` | `DocumentVersion`, `User`, `Department` |
| `PriceCalculation` | `PriceCalculationLine` | `ElectronicCase`, `PriceRule` |
| `Contract` | — | `ElectronicCase` |
| `Payment` | `PaymentConfirmation` | `Contract`, `ElectronicCase` |
| `PerformedWork` | — | `ElectronicCase`, `WorkType`, `Department`, `User`, `DocumentVersion` |
| `AuditLog` | — | everything, weakly (no FK on entity_id) |

Modules (modular monolith, **no microservices**): `auth`, `user`, `applicant`, `application`, `case`,
`workflow`, `document`, `task`, `approval`, `finance`, `work`, `audit`, `admin`, `shared`.
Cross-module access is allowed **only** through each module's public application service and DTOs;
repositories are package-private to their module.

---

## 1. Security & organisation

### 1.1 `Permission`
Spec 16.4 fixes exactly seven actions. A permission is the pair *(section, action)*.

| Field | Type | Notes |
|---|---|---|
| `id` | UUID | PK |
| `section` | enum `PermissionSection` | resource group |
| `action` | enum `PermissionAction` | one of the seven spec actions |
| `code` | String | derived, unique, e.g. `CASE:VIEW` |
| `description` | String | |

`PermissionAction`: `VIEW`, `CREATE`, `EDIT`, `ENDORSE`, `APPROVE`, `SIGN`, `BLOCK`
(spec 16.4: просмотр / создание / изменение / согласование / утверждение / подписание / блокировка).
`PermissionSection`: `APPLICATION`, `CASE`, `PRIMARY_CHECK`, `TASK`, `DOCUMENT`, `APPROVAL`,
`FINANCE`, `PERFORMED_WORK`, `WORKFLOW_CONFIG`, `USER_ADMIN`, `REFERENCE_DATA`, `REPORTING`, `AUDIT`.

Seeded by Flyway, never created at runtime. Cardinality: `Permission 1..1 — 0..* RolePermission`.

### 1.2 `Role`
| Field | Type | Notes |
|---|---|---|
| `id` | UUID | PK |
| `code` | enum-like String | unique |
| `name` | String | |
| `systemRole` | boolean | seeded roles cannot be deleted |

`RoleCode`: `ADMIN`, `APPLICANT`, `ACCOUNTANT`, `HEAD_OF_CERTIFICATION_BODY`, `DEPARTMENT_HEAD`,
`SPECIALIST`, `OPERATOR`.
**Correction (PLAN_REVIEW C2):** spec 3.2 and 3.3 are two distinct roles. `SIGN` belongs only to
`HEAD_OF_CERTIFICATION_BODY`; assigning/reassigning executors belongs only to `DEPARTMENT_HEAD`.

`Role 1..1 — 0..* RolePermission 0..* — 1..1 Permission` (many-to-many, resolved through an owned join
entity so that grants are auditable).

### 1.3 `RolePermission`
`id`, `roleId`, `permissionId`, `grantedAt`, `grantedBy`. Unique `(roleId, permissionId)`.
Every insert/delete raises `ROLE_PERMISSION_CHANGED` in the audit log (spec 16.10).

### 1.4 `Department`
`id`, `code` (unique), `name`, `parentId 0..1` (self-reference, spec 16.12 org structure),
`headUserId 0..1`, `active`, `createdAt`, `updatedAt`.
`Department 0..1 — 0..* Department` (tree). `Department 1..1 — 0..* User`.
**[DEMO]** seeded codes: `MAIN`, `LABORATORY`, `GREEN_CERTIFICATION`, `ACCOUNTING`, `OPERATIONS`.

### 1.5 `Position`
`id`, `code` (unique), `name`, `active`.
Spec 16.13: position is tracked **separately** from role and department; changing it must not change
permissions. Therefore `Position` carries **no** permissions — deliberately.

### 1.6 `User`
| Field | Type | Notes |
|---|---|---|
| `id` | UUID | PK |
| `email` | String | unique, login |
| `passwordHash` | String | BCrypt |
| `fullName` | String | |
| `departmentId` | UUID `0..1` | null for `APPLICANT` |
| `positionId` | UUID `0..1` | |
| `applicantId` | UUID `0..1` | set only for role `APPLICANT` |
| `status` | enum `UserStatus` | `ACTIVE`, `BLOCKED`, `DISABLED` (spec 16.3) |
| `roles` | `Set<Role>` | many-to-many `user_role` |
| `version` | long | optimistic lock |
| `createdAt`,`updatedAt` | Instant | |

`User 0..* — 1..* Role`. Invariant: `applicantId IS NOT NULL` ⇔ user has role `APPLICANT`
(DB CHECK + service validation).

---

## 2. Applicant & catalogue

### 2.1 `Applicant` — **single table, typed** (spec 15.2)
**Correction (PLAN_REVIEW H6).** One entity, discriminator `type`, different mandatory sets enforced
by Bean Validation groups *and* DB CHECK constraints.

| Field | Type | INDIVIDUAL | LEGAL_ENTITY |
|---|---|---|---|
| `id` | UUID | PK | PK |
| `type` | enum `ApplicantType` | required | required |
| `lastName`,`firstName`,`middleName` | String | required (middle optional) | — |
| `birthDate` | LocalDate | required | — |
| `passportSeries`,`passportNumber` | String | required | — |
| `pinfl` | String | required, unique when present | — |
| `orgName` | String | — | required |
| `tin` | String | — | required, unique when present |
| `representativeFullName` | String | — | required |
| `representativePosition` | String | — | required |
| `powerOfAttorneyRef` | String | — | optional (spec: «доверенность при необходимости») |
| `address`,`phone`,`email` | String | required | required |
| `version`,`createdAt`,`updatedAt` | | | |

`ApplicantType`: `INDIVIDUAL`, `LEGAL_ENTITY`.
`Applicant 1..1 — 0..* Application`. `Applicant 1..1 — 0..1 User`.

### 2.2 `Service` (вид услуги, spec 5.1–5.2, 16.5)
`id`, `code` (unique), `name`, `description`, `active`,
`allowedSubmissionChannels` (element collection of `SubmissionChannel`),
`contractRequired` boolean, `paymentRequired` boolean,
`standaloneLaboratory` boolean (spec 9.1/9.2 — a self-standing lab service gets its own case),
`createdAt`, `updatedAt`.
`Service 1..1 — 0..* Application`. `Service 1..1 — 0..* Workflow` (one workflow **code family** per
service; many versions).

### 2.3 `WorkType` — reference table, **not** an enum
Spec 8.2 calls the matrix «первоначальный перечень», i.e. it must be extensible through the admin
panel without code changes (spec 2.6). Hence a table.

`id`, `code` (unique), `name`, `serviceScope` String, `stageKind` String,
`requiresContractAmountBracket` boolean (true only for green-certification expertise and audit, spec 8.4),
`basisDocumentDescription` String, `active`.
**[DEMO]** seeded from the matrix: `TECH_SPEC_AQMS`, `TECH_SPEC_CEMS`, `TECH_SPEC_PGOU`, `TECH_SPEC_LOS`,
`WASTE_CERT_IDENTIFICATION`, `WASTE_CERT_ISSUANCE`, `EXPEDITED_IDENTIFICATION`, `EXPEDITED_LAB_WORK`,
`EXPEDITED_CERT_ISSUANCE`, `OPINION_IDENTIFICATION`, `OPINION_ISSUANCE`,
`CONFORMITY_IDENTIFICATION`, `CONFORMITY_ISSUANCE`, `GREEN_DOC_EXPERTISE`, `GREEN_AUDIT`,
`LAB_AIR_SAMPLING`, `LAB_AIR_ANALYSIS`, `LAB_WASTEWATER_SAMPLING`, `LAB_WASTEWATER_ANALYSIS`.

---

## 3. Workflow configuration (versioned, immutable)

### 3.1 `Workflow` (карточка маршрута, spec 5.2)
| Field | Type | Notes |
|---|---|---|
| `id` | UUID | PK |
| `serviceId` | UUID | `1..1` |
| `code` | String | route family code |
| `version` | int | |
| `name`, `description` | String | |
| `status` | enum `WorkflowStatus` | `DRAFT`, `ACTIVE`, `RETIRED` |
| `mainResponsibleDepartmentId` | UUID | spec 5.4, exactly one |
| `expeditedAllowed` | boolean | spec 5.7 — gate for accounting |
| `contractRequired`, `paymentRequired` | boolean | spec 5.2 |
| `allowExecutionBeforeFullPayment` | boolean | spec 12.8, default `false` |
| `paymentWaitingDays` | int | spec 12.9, **[DEMO]** 10 |
| `totalDeadlineDays` | int | spec 5.2 |
| `approvalRequired` | boolean | spec 5.10 |
| `publishedAt`, `publishedBy` | | |

Unique `(code, version)`. Unique partial: at most one `ACTIVE` version per `code`.
`Workflow 1..1 — 1..* WorkflowStage` (owned). `Workflow 1..1 — 0..* WorkflowTransition` (owned).
`Workflow 1..1 — 0..* ElectronicCase` (a case pins its version).

**Versioning contract (spec 5.12, 16.11):** a published `Workflow` row and everything it owns are
**immutable**. Editing a route = copy-on-write into `version+1` (`DRAFT` → `ACTIVE`), old version →
`RETIRED` but never deleted and never mutated. Cases already pinned to the old version keep running on
it unchanged. `RETIRED` workflows accept no new cases. Spec 16.16: deactivation, not deletion.

### 3.2 `ExternalStage` (spec 5.11, 15.6, 15.7)
**Correction (PLAN_REVIEW H1): N internal stages → 1 external stage.** Not a string on the stage.

`id`, `code` (unique), `nameForApplicant`, `sequence`, `active`.
**[DEMO]** seeded: `REGISTERED`, `DOCUMENTS_UNDER_REVIEW`, `CONTRACT_SENT`, `AWAITING_PAYMENT`,
`WORK_IN_PROGRESS`, `LAB_TESTS_IN_PROGRESS`, `DOCUMENT_UNDER_ENDORSEMENT`, `FINAL_DOCUMENT_PREPARING`,
`DOCUMENT_ON_SIGNING`, `RESULT_ISSUED`, `RETURNED_FOR_CORRECTION`, `REJECTED`.
`ExternalStage 1..1 — 0..* WorkflowStage`.

### 3.3 `WorkflowStage` (spec 5.3)
| Field | Type | Notes |
|---|---|---|
| `id` | UUID | PK |
| `workflowId` | UUID | owner |
| `code` | String | unique within workflow |
| `name` | String | internal name |
| `stageType` | enum `StageType` | see below |
| `sequence` | int | ordering |
| `parallelGroup` | String `0..1` | stages sharing a value run concurrently |
| `required` | boolean | spec 7.14 gating input |
| `externalStageId` | UUID | `1..1` → `ExternalStage` |
| `internalStatusLabel` | String | spec 5.3 «внутренний статус» |
| `responsibleRoleCode` | String `0..1` | spec 5.3 |
| `responsibleDepartmentId` | UUID `0..1` | |
| `assignmentMode` | enum `AssignmentMode` | `DEPARTMENT_HEAD_ASSIGNS`, `ROUTE_FIXED_USER`, `AUTO_ROUND_ROBIN` |
| `deadlineDays` | int `0..1` | traditional-mode deadline |
| `expeditedDeadlineDays` | int `0..1` | spec 5.8 |
| `workTypeId` | UUID `0..1` | if completing this stage records a `PerformedWork` (spec 8) |
| `producesDocumentType` | String `0..1` | |
| `requiresResult` | boolean | task cannot complete without a `TaskResult` |
| `revisionAllowed` | boolean | spec 5.3 |
| `approvalRequired` | boolean | spec 5.10 |
| `approvalMode` | enum `ApprovalMode` `0..1` | `SEQUENTIAL`, `PARALLEL` |

`StageType`: `PRIMARY_CHECK`, `ROUTING`, `ACCOUNTING`, `PAYMENT_CONTROL`, `EXECUTION`,
`ENDORSEMENT`, `FINAL_REVIEW`, `SIGNING`, `COMPLETION`, `NON_APPLICABILITY_OPINION`.

### 3.4 `WorkflowTransition`
`id`, `workflowId`, `fromStageId 0..1` (null = entry), `toStageId`, `conditionType`, `conditionValue`,
`sequence`.
`ConditionType`: `ALWAYS`, `PRIMARY_CHECK_CATEGORY_IN`, `PRIMARY_CHECK_DECISION_IS`,
`PROCESSING_MODE_IS`, `PAYMENT_STATE_SATISFIED`, `ALL_REQUIRED_PARALLEL_TASKS_DONE`,
`APPROVAL_ROUND_COMPLETED`, `MANUAL_DECISION`.
Unique `(workflowId, fromStageId, toStageId, conditionType, conditionValue)`.
**The route is data-driven — no if/else chain encodes the business process.**

---

## 4. Application → ElectronicCase

### 4.1 `Application` (spec 1.3, 1.4, 4.3, 4.4)
`id`, `number` (unique, generated), `applicantId 1..1`, `serviceId 1..1`,
`submissionChannel` enum, `registeredById 0..1` (the employee, mandatory when channel = `PAPER`),
`submittedAt`, `registeredAt`, `status` enum `ApplicationStatus`, `formData` JSONB (spec 5.2 «обязательные
сведения заявки» are route-configured), `version`, `createdAt`, `updatedAt`.

`SubmissionChannel`: `PERSONAL_CABINET`, `SINGLE_WINDOW`, `OTHER_SERVICE`, `PAPER` (spec 1.3).
`ApplicationStatus`: `DRAFT`, `SUBMITTED`, `REGISTERED`, `RETURNED_TO_APPLICANT`, `CANCELLED`.
`Application 1..1 — 1..1 ElectronicCase` (created atomically on registration, spec 1.4).
**Correction (PLAN_REVIEW H7):** a paper application is registered by staff, so `registerApplication`
is not an applicant-only operation — this drives the security rule in `SECURITY_SPEC.md`.

### 4.2 `ElectronicCase` — central object (spec 1.4)
**Correction (PLAN_REVIEW M1 / FIX 7):** five independent fields; status is a lifecycle, position in
the route lives in `currentStageId`.

| Field | Type | Notes |
|---|---|---|
| `id` | UUID | PK, the «уникальный системный ID» |
| `caseNumber` | String | unique, human-readable |
| `applicationId` | UUID | `1..1`, unique |
| `applicantId` | UUID | denormalised for ownership checks + index |
| `serviceId` | UUID | |
| `workflowId` | UUID | **pinned version** (spec 5.12) |
| `status` | enum `CaseStatus` | lifecycle |
| `currentStageId` | UUID `0..1` | → `WorkflowStage`; null when several parallel stages are active |
| `primaryCheckCategory` | enum `0..1` | independent field (spec 1.5) |
| `primaryCheckDecision` | enum `0..1` | independent field (spec 4.7) |
| `processingMode` | enum `0..1` | set by accounting only (spec 1.9) |
| `processingModeSetById`, `processingModeSetAt` | | |
| `mainResponsibleDepartmentId` | UUID | copied from workflow at creation |
| `participatingDepartmentIds` | Set<UUID> | element collection |
| `dueAt` | Instant `0..1` | derived from workflow + mode |
| `paymentDueAt` | Instant `0..1` | spec 12.9 |
| `paymentOverdue` | boolean | set by scheduler, never auto-rejects |
| `completedAt`, `rejectedAt`, `rejectionReason` | | |
| `version` | long | **optimistic lock — the workflow concurrency guard** |
| `createdAt`, `updatedAt` | | |

`CaseStatus`: `REGISTERED`, `PRIMARY_CHECK`, `PRIMARY_CHECK_DONE`, `IN_ACCOUNTING`, `WAITING_PAYMENT`,
`IN_EXECUTION`, `FINAL_REVIEW`, `ON_SIGNING`, `COMPLETED`, `RETURNED`, `REJECTED`.
`PrimaryCheckCategory`: `RED`, `YELLOW`, `GREEN` **[DEMO criteria — ASSUMPTIONS A1]**.
`PrimaryCheckDecision`: `ACCEPTED`, `RETURNED_TO_APPLICANT`, `NON_APPLICABILITY_OPINION`,
`ROUTE_CHANGED`, `REJECTED` (spec 4.7) **[DEMO mapping — A2]**.
`ProcessingMode`: `TRADITIONAL`, `EXPEDITED`.

Relations: `1..1 Application`, `1..1 Workflow`, `1..1 Applicant`, `1..1 Service`,
owns `0..1 PrimaryCheck`, `0..* CaseItem`, `1..* CaseStage`, `0..* CaseComment`;
referenced by `0..* Task`, `0..* Document`, `0..* PriceCalculation`, `0..1 Contract`, `0..1 Payment`,
`0..* PerformedWork`, `0..* AuditLog`.

### 4.3 `PrimaryCheck` (spec 4.5–4.7)
Separate entity so the check itself is auditable and repeatable after a return.
`id`, `caseId 1..1`, `performedById`, `performedAt`, `category`, `decision`, `reason`,
`checklist` JSONB, `newWorkflowId 0..1` (when decision = `ROUTE_CHANGED`), `version`.
`ElectronicCase 1..1 — 0..* PrimaryCheck` (a return-and-resubmit produces a second check row; the
case fields mirror the latest).

### 4.4 `CaseItem` (spec 12.6) — **the pricing item composition**
**Correction (PLAN_REVIEW H2).** Without this, «хранение состава позиций, на основании которых
сформирована договорная сумма» is unimplementable.
`id`, `caseId 1..1`, `lineNo`, `itemName`, `itemCode 0..1`, `quantity` (numeric), `unit`,
`objectAddress 0..1`, `attributes` JSONB, `createdAt`, `updatedAt`.
`ElectronicCase 1..1 — 0..* CaseItem` (owned, cascade).

### 4.5 `CaseStage` — **runtime stage instance** (design addition)
Not in either input document, but required: the route config is immutable, so per-case stage state needs
its own row. This single table is what makes parallel gating, idempotency and «current stage» honest.

`id`, `caseId 1..1`, `workflowStageId 1..1`, `status` enum `CaseStageStatus`,
`parallelGroup 0..1` (copied), `required` boolean (copied), `activatedAt`, `completedAt`,
`dueAt`, `overdue` boolean, `activationCount` int, `version`.
`CaseStageStatus`: `PENDING`, `ACTIVE`, `COMPLETED`, `SKIPPED`, `RETURNED`, `CANCELLED`.
Unique `(caseId, workflowStageId)` → re-activating a stage is idempotent by construction.
`ElectronicCase 1..1 — 1..* CaseStage` (owned). `CaseStage 1..1 — 0..* Task`.

### 4.6 `CaseComment` (spec 13.5.1, 17.4, 17.8)
**Correction (PLAN_REVIEW M6).** Distinct from approval remarks and never shown to the applicant.
`id`, `caseId 1..1`, `documentVersionId 0..1`, `authorId`, `authorDepartmentId`, `body`,
`visibility` enum (`INTERNAL` only in this slice), `createdAt`.

---

## 5. Execution block

### 5.1 `Task` (spec 7.3, 7.4)
| Field | Type | Notes |
|---|---|---|
| `id` | UUID | PK |
| `caseId` | UUID | `1..1` |
| `caseStageId` | UUID | `1..1` |
| `workflowStageId` | UUID | denormalised for config lookup |
| `title`, `description` | String | |
| `assignedDepartmentId` | UUID | from stage config |
| `assignedUserId` | UUID `0..1` | set by department head (spec 5.5) |
| `assignedById`, `assignedAt` | | |
| `status` | enum `TaskStatus` | |
| `processingMode` | enum | copied, drives deadline (spec 5.8) |
| `deadline` | Instant `0..1` | |
| `overdue` | boolean | spec 17.2/17.6 |
| `startedAt`, `completedAt` | | |
| `revisionCount` | int | |
| `version` | long | optimistic lock |

`TaskStatus`: `CREATED`, `ASSIGNED`, `IN_PROGRESS`, `SUBMITTED_FOR_REVIEW`, `COMPLETED`,
`RETURNED_FOR_REVISION`, `CANCELLED`.
`Task 1..1 — 1..* TaskResult` (owned, versioned). `Task 1..1 — 0..* Document`.
**Reassignment** (spec 3.3, 7.3) is a first-class operation raising `TASK_REASSIGNED`
(PLAN_REVIEW M4).

### 5.2 `TaskResult` (spec 7.13, 7.13.1)
Append-only version chain. A completed result is **never** updated in place.
`id`, `taskId 1..1`, `versionNo` int, `payload` JSONB, `summary`, `authorId`, `createdAt`,
`status` enum `TaskResultStatus`, `supersedesId 0..1` (self-reference),
`revisionReason 0..1`, `returnedById 0..1`, `returnedAt 0..1`, `approvedById 0..1`, `approvedAt 0..1`.
`TaskResultStatus`: `DRAFT`, `SUBMITTED`, `APPROVED`, `SUPERSEDED`, `REJECTED`.
Unique `(taskId, versionNo)`. Partial unique: at most one non-`SUPERSEDED` `APPROVED` per task.
**Versioning contract:** correcting an approved result requires `returnTaskToRevision` → new
`TaskResult` with `versionNo+1` and `supersedesId` pointing at the old row, which flips to `SUPERSEDED`
and keeps its payload, author and timestamps (spec 7.13).

---

## 6. Documents & approval

### 6.1 `Document` (spec 6.5, 13.1) — **mandatory, not optional**
**Correction (PLAN_REVIEW C1).**
`id`, `caseId 1..1`, `taskId 0..1`, `documentType` String (reference-configurable),
`title`, `status` enum `DocumentStatus`, `currentVersionId 0..1`, `createdById`, `version` (lock),
`createdAt`, `updatedAt`.
`DocumentStatus`: `DRAFT`, `UNDER_ENDORSEMENT`, `RETURNED_FOR_REVISION`, `ENDORSED`, `SIGNED`,
`CANCELLED`.
`Document 1..1 — 1..* DocumentVersion` (owned).

### 6.2 `DocumentVersion` — immutable
`id`, `documentId 1..1`, `versionNo` int, `contentRef` String (storage key),
`contentHash` String (SHA-256, spec 7.13.1 integrity), `fileName`, `mimeType`, `sizeBytes`,
`fields` JSONB (template placeholders filled from the case, spec 6.3/6.4),
`createdById`, `createdAt`, `status` enum `DocumentVersionStatus`,
`supersedesId 0..1`, `revisionReason 0..1`, `signedById 0..1`, `signedAt 0..1`.
`DocumentVersionStatus`: `DRAFT`, `UNDER_ENDORSEMENT`, `ENDORSED`, `REJECTED`, `SUPERSEDED`, `SIGNED`.
Unique `(documentId, versionNo)`. **No UPDATE of content or hash after creation** (enforced by trigger).
`DocumentVersion 1..1 — 0..* ApprovalRound`.
**Versioning contract (spec 6.6, 13.5):** a reviewed version is never overwritten. Remarks stay attached
to the exact version they were written against; a correction creates `versionNo+1`, and the whole
endorsement history of the previous version remains queryable.

### 6.3 `ApprovalRound` (spec 13.3, 13.7)
**Correction (PLAN_REVIEW C3).**
`id`, `documentVersionId 1..1`, `caseId` (denormalised), `mode` enum `ApprovalMode`
(`SEQUENTIAL`|`PARALLEL`), `roundNo` int, `status` enum `ApprovalRoundStatus`,
`initiatedById`, `initiatedAt`, `completedAt 0..1`, `version`.
`ApprovalRoundStatus`: `IN_PROGRESS`, `COMPLETED_APPROVED`, `COMPLETED_REJECTED`, `CANCELLED`.
Unique `(documentVersionId, roundNo)`.
`ApprovalRound 1..1 — 1..* ApprovalTask` (owned).

### 6.4 `ApprovalTask` — one per participant (spec 13.3 «по каждому участнику … отдельная задача»)
`id`, `approvalRoundId 1..1`, `participantUserId 0..1`, `participantDepartmentId 0..1`,
`participantKind` enum (`USER`, `DEPARTMENT`, `APPLICANT`, `ACCOUNTING`),
`required` boolean, `sequenceNo` int (meaningful when `SEQUENTIAL`),
`status` enum `ApprovalTaskStatus`, `comment 0..1`, `decidedById 0..1`, `decidedAt 0..1`,
`dueAt 0..1`, `version`.
`ApprovalTaskStatus`: `SENT`, `IN_REVIEW`, `APPROVED`, `REJECTED`, `SKIPPED`.
Invariants: `status = REJECTED ⇒ comment IS NOT NULL` (spec 13.4, DB CHECK + service);
round completes `COMPLETED_APPROVED` only when **every** `required` task is `APPROVED` (spec 7.11, 13.7);
the applicant can be a participant (spec 13.6).

---

## 7. Finance

### 7.1 `PriceRule` (spec 12.2, 16.15) — **[DEMO values, A3]**
`id`, `serviceId 0..1`, `workflowId 0..1`, `workTypeId 0..1`, `ruleType` enum,
`processingMode 0..1`, `basePrice` numeric(18,2) `0..1`, `coefficient` numeric(10,4) `0..1`,
`currency` (`UZS`), `validFrom`, `validTo 0..1`, `demo` boolean **(always `true` in this slice)**,
`active`, `createdAt`.
`PriceRuleType`: `BASE_PER_ITEM`, `MODE_COEFFICIENT`, `ADDITIONAL_WORK_FEE`, `MINIMUM_TOTAL`.
Rules are **superseded by validity window, never edited**, so an old calculation stays reproducible.

### 7.2 `PriceCalculation` (spec 1.11, 1.12, 12.3, 12.4)
Immutable snapshot; a recalculation creates a **new** row.
`id`, `caseId 1..1`, `calculationNo` int, `processingMode`, `calculatedTotal` numeric(18,2),
`currency`, `ruleSetSnapshot` JSONB, `calculatedAt`, `trigger` enum
(`INITIAL`, `MODE_CHANGED`, `ITEMS_CHANGED`, `MANUAL_RECALC`), `supersedesId 0..1`,
`status` enum (`ACTIVE`, `SUPERSEDED`, `CONFIRMED`).
Unique `(caseId, calculationNo)`. Partial unique: one `ACTIVE` per case.
**Correction (PLAN_REVIEW H5 / FIX 9):** changing `processingMode` **before** price confirmation
automatically produces a `MODE_CHANGED` recalculation (spec 12.3).
`PriceCalculation 1..1 — 1..* PriceCalculationLine` (owned): `id`, `priceCalculationId`, `caseItemId 0..1`,
`priceRuleId 0..1`, `description`, `quantity`, `unitPrice`, `coefficient`, `lineTotal`.

### 7.3 `Contract` (spec 12.5, 12.10, 13/1.13)
`id`, `caseId 1..1` (unique), `contractNumber 0..1`, `contractDate 0..1`,
`calculatedAmount` (copy of the confirmed calculation — spec 12.4 «сохраняет первоначальный расчет»),
`actualAmount 0..1`, `amountChangedById 0..1`, `amountChangedAt 0..1`, `amountChangeReason 0..1`,
`currency`, `sent` boolean, `sentAt 0..1`, `sentChannel` enum (`DIDOX`, `OTHER`),
`invoiceReference 0..1`, `invoiceDate 0..1` (spec 12.10 — reference only, CRM does not issue it),
`version`, `createdAt`, `updatedAt`.
Invariant: `actualAmount` set to a value ≠ `calculatedAmount` **requires** `amountChangedById`,
`amountChangedAt` (DB CHECK). The calculated value is never overwritten.

### 7.4 `Payment` (spec 12.7, 12.8)
`id`, `caseId 1..1` (unique), `contractId 0..1`, `status` enum `PaymentStatus`,
`contractAmount`, `confirmedAmount` (default 0), `debtAmount` (generated/maintained),
`waitingSince 0..1`, `dueAt 0..1`, `overdue` boolean, `version`, `createdAt`, `updatedAt`.
`PaymentStatus`: `WAITING_PAYMENT`, `PAID`, `PARTIALLY_PAID`, `DEBT`, `NOT_CONFIRMED`
(exactly spec 12.7: ожидается оплата / оплачено / частично оплачено / задолженность / не подтвержден).
`Payment 1..1 — 0..* PaymentConfirmation` (owned): `id`, `paymentId`, `amount`, `confirmedById`,
`confirmedAt`, `note`, `externalReference 0..1`.
**CRM never processes money** (spec 1.14, 12.7) — accounting only records confirmations.
**Waiting period (spec 12.9):** `dueAt = waitingSince + workflow.paymentWaitingDays` **[DEMO 10, A5]**.
On expiry a scheduler sets `overdue`, raises `PAYMENT_OVERDUE`, and creates a decision task for the
authorized manager. **The system never auto-rejects** — spec 12.9 reserves that decision for a manager.

---

## 8. Performed-works accounting (spec section 8) — **[missing from MASTER_PLAN]**

### `PerformedWork`
**Correction (PLAN_REVIEW H3).**
`id`, `caseId 1..1`, `workTypeId 1..1`, `caseStageId 0..1`, `workflowStageId 0..1`,
`departmentId`, `executorUserId`, `serviceId`, `processingMode`,
`performedAt`, `recordedAt`, `recordedById`,
`supportingDocumentVersionId 0..1` (spec 8.3 «связанный подтверждающий документ»),
`invoiceReference 0..1` (matrix column «Основание для расчета» = document + invoice),
`contractAmountBracket` enum `0..1`, `countable` boolean (always `true`; kept for auditability),
`createdAt`.
`ContractAmountBracket`: `LT_10M`, `M10_20M`, `M20_30M`, `GT_30M` (spec 8.4 — the four ranges are
fixed by the spec; **boundary handling is [DEMO] per A4**: lower inclusive, upper exclusive).
Invariants:
- **Unique `(caseId, workTypeId, caseStageId)`** — one real work is counted once (spec 8.5).
- A `RETURN_FOR_REVISION` cycle does **not** insert a new row (spec 8.5), it only updates
  `supportingDocumentVersionId` on the existing row.
- Lab work inside an expedited certification route is recorded against the **same case** with
  `workTypeId = EXPEDITED_LAB_WORK`; no separate «срочная лабораторная услуга» row (spec 8.5, 7.9).
- `workType.requiresContractAmountBracket = true ⇒ contractAmountBracket IS NOT NULL` (CHECK).
Bonuses/rates (spec 8.6) are **out of scope** — the CRM only records the accounting basis.

---

## 9. Audit

### `AuditLog` (spec 1.19, 7.16.1, 12.11, 16.10, 18.12, 20.3)
**Correction (PLAN_REVIEW C4).**
`id` (UUID), `seq` (bigserial — chain order), `caseId 0..1` **NULLABLE** (admin events have no case),
`taskId 0..1`, `userId 0..1` (null for scheduler), `actorRoleCode 0..1`, `actorDepartmentId 0..1`,
`action` enum `AuditAction`, `entityType` String, `entityId` UUID (no FK — weak reference),
`oldValue` JSONB `0..1`, `newValue` JSONB `0..1`, `reason 0..1`,
`ipAddress 0..1`, `createdAt`,
`prevHash` String `0..1`, `rowHash` String (SHA-256 chain, A8).

`AuditAction` (minimum set, spec-driven):
`CASE_CREATED`, `CASE_REGISTERED`, `PRIMARY_CHECK_COMPLETED`, `CATEGORY_ASSIGNED`,
`PRIMARY_CHECK_DECISION_RECORDED`, `ROUTE_ASSIGNED`, `ROUTE_CHANGED`, `PROCESSING_MODE_SET`,
`PRICE_CALCULATED`, `PRICE_RECALCULATED`, `PRICE_CONFIRMED`, `PRICE_CHANGED`,
`CONTRACT_RECORDED`, `CONTRACT_SENT`, `PAYMENT_CONFIRMED`, `PAYMENT_STATUS_CHANGED`,
`PAYMENT_OVERDUE`, `STAGE_ACTIVATED`, `STAGE_COMPLETED`,
`TASK_CREATED`, `TASK_ASSIGNED`, `TASK_REASSIGNED`, `TASK_STARTED`, `TASK_COMPLETED`,
`TASK_RETURNED`, `RESULT_VERSION_CREATED`, `RESULT_APPROVED`,
`DOCUMENT_CREATED`, `DOCUMENT_VERSION_CREATED`, `APPROVAL_ROUND_STARTED`, `APPROVAL_SENT`,
`APPROVAL_APPROVED`, `APPROVAL_REJECTED`, `APPROVAL_ROUND_COMPLETED`,
`DOCUMENT_SIGNED`, `PERFORMED_WORK_RECORDED`,
`CASE_COMPLETED`, `CASE_REJECTED`, `CASE_RETURNED_TO_APPLICANT`,
`USER_CREATED`, `USER_BLOCKED`, `ROLE_PERMISSION_CHANGED`, `WORKFLOW_PUBLISHED`,
`WORKFLOW_RETIRED`, `PRICE_RULE_CHANGED`, `REFERENCE_DATA_CHANGED`,
`REPORTING_ACCESS_CHANGED`, `CONFIDENTIAL_DATA_ACCESSED` (spec 16.18).

**Immutability (spec 20.3, three layers):** no update/delete endpoint; the application DB role holds
only `INSERT, SELECT`; a `BEFORE UPDATE OR DELETE` trigger raises. Plus the hash chain so tampering by
a superuser is *detectable* even if not preventable. Writing an audit row is done by the domain services
through one `AuditWriter` port — there is **no** generic "post an audit event" REST endpoint.

---

## 10. Optimistic locking summary (`@Version`)

| Entity | Why |
|---|---|
| `ElectronicCase` | the workflow-advance guard: two concurrent advances → one `OptimisticLockException` |
| `CaseStage` | duplicate stage activation |
| `Task` | duplicate completion / concurrent reassignment |
| `Document` | concurrent version creation racing on `currentVersionId` |
| `ApprovalRound`, `ApprovalTask` | concurrent decisions closing the round twice |
| `Payment`, `Contract` | concurrent confirmations changing the amount |
| `Applicant`, `User` | ordinary stale-update protection |

**Deliberately not versioned:** `TaskResult`, `DocumentVersion`, `PriceCalculation`,
`PaymentConfirmation`, `PerformedWork`, `AuditLog` — append-only rows are never updated, so a version
column would be dead weight.
