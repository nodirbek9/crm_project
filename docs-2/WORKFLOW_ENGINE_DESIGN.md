# WORKFLOW_ENGINE_DESIGN.md

The route is **data**, not code. There is no `if (service == X)` anywhere in the engine. Spec 1.6, 5.12
and 16.14 require route changes through the admin panel without touching code, so a giant conditional
would be a direct violation of the requirement, not merely bad taste.

## 1. Components

| Component | Responsibility |
|---|---|
| `WorkflowDefinitionService` | reads/publishes route config; copy-on-write versioning |
| `WorkflowEngine` | the only thing allowed to move a case: `activateStage`, `completeStage`, `advance` |
| `TransitionEvaluator` | evaluates one `WorkflowTransition.conditionType` against case state |
| `ConditionHandler` (interface, one impl per `ConditionType`) | pluggable predicates, registered in a map |
| `StageActivator` (interface, one impl per `StageType`) | side effects of entering a stage (create a task, open a payment, start an endorsement round) |
| `TaskFactory` | builds a `Task` from `WorkflowStage` + `ProcessingMode` |
| `DeadlineCalculator` | stage deadline from mode (spec 5.8) |
| `PerformedWorkRecorder` | records section-8 accounting when a stage completes |
| `AuditWriter` | append-only audit port |

Adding a new condition or stage type = adding one class, no engine change. That is the extensibility
claim, and it is the thing an interviewer will poke at.

## 2. Workflow versioning

**Model:** a `workflow` row is identified by `(code, version)`. A published row and all of its
`workflow_stage` / `workflow_transition` children are treated as **immutable**.

**Publishing:**
1. `POST /api/workflows/{code}/versions` deep-copies the current `ACTIVE` version into `version+1`
   with status `DRAFT`.
2. The draft is edited freely (it owns no cases).
3. `POST /api/workflows/{id}/publish` runs validation (below), then in one transaction sets the draft
   `ACTIVE` and the previous `ACTIVE` → `RETIRED`. The partial unique index
   `uq_workflow_one_active` makes a double publish impossible.
4. `WORKFLOW_PUBLISHED` + `WORKFLOW_RETIRED` audit rows are written (spec 16.10).

**Binding:** `electronic_case.workflow_id` pins the exact version at registration. It is never
rewritten, except by an explicit `ROUTE_CHANGED` primary-check decision (spec 4.7), which is a
deliberate, audited, user-taken action — not a side effect of configuration.

**Guarantee (spec 5.12, 16.11):** publishing v2 cannot alter any case running on v1, because v1's rows
are never mutated and the case never re-resolves its route by code. Test W-11 proves it: register a
case on v1, publish v2 that deletes a stage, then drive the old case to completion.

**Retirement rules:** `RETIRED` accepts no new cases; `service.active = false` and
`workflow_stage`-level deactivation are how an admin turns things off without deleting history
(spec 16.16).

**Rejected alternatives:** (a) mutable route + effective-dated rows — every historical query becomes
temporal, and one bad UPDATE rewrites the past; (b) snapshotting the whole route as JSON onto the case
— honest history, but the config becomes unqueryable, so "which cases sit on stage LABORATORY" turns
into a JSON scan. The versioned-rows model keeps both history and queryability. Say this out loud in
the interview; the trade-off is the answer, not the choice.

**Publish-time validation:** exactly one entry transition; every stage reachable from the entry; no
cycle except explicit revision edges; every `parallel_group` has at least one `required = true` member
(otherwise the gate can never be satisfied meaningfully); every `approval_required` stage has an
`approval_mode`; exactly one `COMPLETION` stage; `expedited_deadline_days <= deadline_days` where both
are set.

## 3. Case initialisation (spec 1.4, 4.4)

`registerApplication()` in ONE transaction:
1. validate the application against `service` + route-required fields;
2. generate `application.number` and `case_number` (sequence-backed, unique);
3. resolve the `ACTIVE` workflow for the service → pin `workflow_id`;
4. create `electronic_case` with `status = REGISTERED`, copy `main_responsible_department_id`;
5. materialise **one `case_stage` row per `workflow_stage`** with `status = PENDING`, copying
   `required` and `parallel_group`;
6. resolve the entry transition (`from_stage_id IS NULL`) and activate that stage;
7. write `CASE_CREATED`, `CASE_REGISTERED`, `STAGE_ACTIVATED`.

Materialising all stages up front (rather than lazily) is what makes the gating query a plain indexed
read instead of a graph walk, and makes "where is this case" answerable with one row per stage.

## 4. Stage activation

```
activateStage(caseId, workflowStageId):
  case  <- load ElectronicCase (optimistic lock)
  stage <- load CaseStage by (caseId, workflowStageId)     # unique
  if stage.status in (ACTIVE, COMPLETED): return stage     # IDEMPOTENT no-op
  stage.status = ACTIVE
  stage.activatedAt = now
  stage.activationCount += 1
  stage.dueAt = DeadlineCalculator.of(stageConfig, case.processingMode)
  case.status = lifecycleFor(stageConfig.stageType)
  case.currentStageId = (single active stage) ? stage.workflowStageId : null
  StageActivator.for(stageConfig.stageType).onActivate(case, stage)
  audit STAGE_ACTIVATED
```

`current_stage_id` is deliberately `NULL` while a parallel group is open — the honest answer to "which
stage is this case on" is "several", and the applicant sees the mapped `external_stage` anyway.

`StageActivator` per type:
- `PRIMARY_CHECK`, `EXECUTION`, `FINAL_REVIEW` → create a `Task` for the responsible department;
- `ACCOUNTING` → nothing automatic; waits for the accountant's commands;
- `PAYMENT_CONTROL` → create/refresh `payment`, set `waiting_since = now`,
  `due_at = now + workflow.payment_waiting_days` **[DEMO 10]**, mirror to `case.payment_due_at`;
- `ENDORSEMENT` → open an `ApprovalRound` over the current `DocumentVersion` and fan out one
  `ApprovalTask` per configured participant;
- `SIGNING` → mark the document version `UNDER_ENDORSEMENT → ENDORSED`, await the signature;
- `COMPLETION` → `case.status = COMPLETED`, `completed_at = now`, audit `CASE_COMPLETED`.

## 5. Sequential execution

Completing a stage asks the transition table for outgoing edges ordered by `sequence`, evaluates each
condition, and activates every stage whose condition holds:

```
completeStage(caseId, stageId):
  stage <- load CaseStage
  if stage.status == COMPLETED: return          # IDEMPOTENT
  require stage.status == ACTIVE
  stage.status = COMPLETED; stage.completedAt = now
  audit STAGE_COMPLETED
  PerformedWorkRecorder.recordIfConfigured(case, stage)   # spec 8
  for t in transitions(from = stageId) order by sequence:
      if TransitionEvaluator.matches(t, case): activateStage(case, t.toStageId)
```

Several matching edges from one stage = a parallel fan-out (see the demo route: `IDENTIFICATION` →
`LABORATORY` + `EXPERT_REVIEW` + `AUDIT`). No special "fork" concept is needed.

## 6. Parallel execution and gating (spec 7.14, 4.15)

`ALL_REQUIRED_PARALLEL_TASKS_DONE` with `conditionValue = <parallelGroup>`:

```
matches(t, case):
  group   <- t.conditionValue
  siblings <- SELECT * FROM case_stage
              WHERE case_id = :case AND parallel_group = :group AND required = true
              FOR UPDATE                       # serialises concurrent gate checks
  return siblings.allMatch(s -> s.status == COMPLETED)
```

Three properties this buys:
1. an incomplete **required** sibling blocks the dependent stage (test W-05);
2. an incomplete **optional** sibling does not (test W-06 — the demo route's `AUDIT` is optional
   exactly to make this observable);
3. when the last sibling completes, the dependent stage activates **once**, because the row lock
   serialises the two racing evaluations and the loser then finds `case_stage.status = ACTIVE` and
   no-ops (test C-02).

`FOR UPDATE` here rather than optimistic locking: the gate reads *sibling* rows and writes a *different*
row, so a version bump on the case alone would not order the two readers. This is the one place where
the cheap trick is not enough, and knowing why is the point.

## 7. Task generation and completion

**Generation.** `TaskFactory` builds from stage config: `assigned_department_id` from
`responsible_department_id`, `deadline` from `DeadlineCalculator`, `processing_mode` copied from the
case, status `CREATED`. Assignment mode decides what happens next:
`DEPARTMENT_HEAD_ASSIGNS` → stays `CREATED` until the head assigns (spec 5.5);
`ROUTE_FIXED_USER` → auto-assign the department head; `AUTO_ROUND_ROBIN` → **[DEMO]** least-open-tasks
member of the department.

**Assignment / reassignment** (spec 3.3, 7.3): only the head of the task's department (or ADMIN? **no** —
spec 16.17 forbids it). `TASK_ASSIGNED` / `TASK_REASSIGNED` audited.

**Completion.**
```
completeTask(taskId, resultPayload, actor):
  task <- load Task (optimistic lock)
  if task.status == COMPLETED: return current view       # IDEMPOTENT replay
  require task.status in (ASSIGNED, IN_PROGRESS, RETURNED_FOR_REVISION)
  require actor == task.assignedUser
  if stageConfig.requiresResult:
      require a SUBMITTED TaskResult exists            -> else 422 RESULT_REQUIRED
  task.status = COMPLETED; task.completedAt = now
  audit TASK_COMPLETED
  engine.completeStage(task.caseId, task.workflowStageId)
```
A task that requires a result cannot complete without one (test T-03) — spec 7.5.7's principle applied
to the non-field case.

**Two-step review.** Where the stage has a reviewer, the specialist moves the task to
`SUBMITTED_FOR_REVIEW` and the department head either approves (→ `COMPLETED`, result `APPROVED`,
spec 4.15) or returns it.

## 8. Revision (spec 7.13, 13.4)

```
returnTaskToRevision(taskId, reason, actor):
  require stageConfig.revisionAllowed
  require reason is present                              -> else 400
  require actor is the department head or the reviewer
  live <- current live TaskResult
  live.status = SUPERSEDED; live.returnedBy = actor; live.returnedAt = now
  task.status = RETURNED_FOR_REVISION; task.revisionCount += 1
  caseStage.status = RETURNED                            # stage re-opens, no new stage row
  audit TASK_RETURNED
```
Then the specialist submits a **new** `TaskResult` with `version_no + 1`, `supersedes_id` → the old row
and `revision_reason` copied. The old payload, author and timestamps stay readable forever
(`ck_task_result_supersede` + `task_result_guard()` make the alternative impossible).

The same shape applies to documents: a rejected `ApprovalRound` sets the `DocumentVersion` to
`REJECTED`, the author creates `version_no + 1`, and a **new** `ApprovalRound` (`round_no + 1`) is
opened over the new version. Remarks stay bound to the version they were written against (spec 13.5).

**Critical interaction with section 8.5:** a revision cycle must **not** create a second
`PerformedWork`. `PerformedWorkRecorder` therefore upserts on
`(case_id, work_type_id, case_stage_id)` — on conflict it only refreshes
`supporting_document_version_id`. Test PW-02.

## 9. Endorsement rounds (spec 13.3, 13.7)

`PARALLEL`: every `ApprovalTask` is `SENT` at once.
`SEQUENTIAL`: only `sequence_no = 0` is `SENT`; approving one sends the next.
Round completion, evaluated after every decision inside the same transaction:
- any `required` task `REJECTED` → round `COMPLETED_REJECTED`, document version `REJECTED`,
  revision path;
- all `required` tasks `APPROVED` → round `COMPLETED_APPROVED`, version `ENDORSED`, engine advances
  through `APPROVAL_ROUND_COMPLETED`;
- otherwise stay `IN_PROGRESS`.

`uq_approval_round_one_open` prevents two open rounds on one version; the `approval_round.version`
column prevents two concurrent decisions both closing the round.

## 10. Payment gating (spec 12.8, 12.9)

`PAYMENT_STATE_SATISFIED`:
```
matches: payment.status == PAID
      || (workflow.allowExecutionBeforeFullPayment && payment.status == PARTIALLY_PAID)
```
`PaymentWaitingScheduler` (fixed delay, **[DEMO]** every 15 min) picks up
`electronic_case` rows past `payment_due_at` via the partial index, sets `payment_overdue = true`,
writes `PAYMENT_OVERDUE`, and creates a decision task for the authorized manager.
**It never rejects the case** — spec 12.9 reserves that decision for a person. Stating that constraint
explicitly is worth more than automating it.

## 11. Idempotency summary

| Operation | Mechanism |
|---|---|
| stage activation | `uq_case_stage` + status pre-check → no-op replay |
| task completion | status pre-check → returns the same view, does not advance twice |
| stage completion | status pre-check |
| performed-work recording | `uq_performed_work_once` + upsert |
| payment confirmation | `uq_payment_conf_external` |
| approval decision | `approval_task.status` pre-check + `approval_round.version` |
| any mutating POST | optional `Idempotency-Key` header → `command_log` |
| workflow publish | partial `uq_workflow_one_active` |

## 12. Concurrency summary

| Race | Guard | Observable result |
|---|---|---|
| two `completeTask` on one task | `task.version` + status pre-check | second is a no-op replay, stage advances once |
| two siblings finishing simultaneously | `SELECT ... FOR UPDATE` on group + `uq_case_stage` | next stage activates exactly once |
| two `confirmPayment` | `payment.version` + `uq_payment_conf_external` | `409` or a rejected duplicate |
| accountant confirms price while another edits items | `electronic_case.version` | `409 CONCURRENT_MODIFICATION` |
| two approvers deciding last | `approval_round.version` | one closes the round, other gets `409` |
| duplicate `POST /register` | `Idempotency-Key` → `command_log` | stored response replayed |
| stale case update | `electronic_case.version` | `409` |

## 13. Transaction boundaries and why each exists

| Method | Boundary rationale |
|---|---|
| `registerApplication` | application number + case + N stage rows + first activation + audit must be atomic; a half-registered case has no owner (spec 1.4) |
| `completePrimaryCheck` | category, decision, case fields and the route transition are one business fact (spec 4.6) |
| `setProcessingMode` | mode + automatic recalculation must not be separable (spec 12.3) |
| `confirmPrice` | confirmation, contract `calculated_amount` snapshot and audit are one fact (spec 12.4) |
| `recordContract` | number/date/amount/sent flag are validated as a set (spec 12.5) |
| `confirmPayment` | ledger insert + status/debt recompute + possible advance (spec 12.7) |
| `completeTask` | result check + task status + stage completion + performed-work + advance; a partially advanced route is unrecoverable |
| `returnTaskToRevision` | supersede + task status + stage reopen + audit (spec 7.13) |
| `createDocumentVersion` | version insert + `document.current_version_id` move |
| `decideApproval` | participant decision + round evaluation + possible advance (spec 13.7) |
| `sign` | version `SIGNED` + case `COMPLETED` + performed-work + audit (spec 14.6) |
| `advanceWorkflow` | the gate read and the activation must see one snapshot |
| `publishWorkflow` | activate new + retire old (spec 16.11) |

All are `@Transactional` with default `REQUIRED`; the engine is always called **inside** the caller's
transaction, never in a new one, so audit and business change can never diverge. No `@Async` anywhere
in the slice — the only scheduled component is the payment-expiry scanner.
