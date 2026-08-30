# API_SPEC.md

Base path `/api`. JSON only. JWT bearer except `/api/auth/login`. All 42 endpoints below are in scope
for the slice. Controllers are thin: validate → call one application service → map to DTO.
**No JPA entity is ever serialized.**

## 0. Conventions

* Auth: `Authorization: Bearer <access token>`.
* Mutating `POST`/`PATCH` accept an optional `Idempotency-Key` header (see `command_log`).
* Optimistic locking: clients echo `version` in the request body on state-changing PATCHes; a mismatch
  is `409`.
* Paging: `?page=0&size=20&sort=createdAt,desc`, response `PageResponse<T>{content,page,size,totalElements,totalPages}`.
* Errors: one shape everywhere (§9).
* `403` vs `404`: an applicant touching another applicant's case gets **404** (no existence leak); a
  staff member lacking a permission on an object they may know exists gets **403**. Rationale in
  `SECURITY_SPEC.md` §6.

## 1. Auth

| Method | Path | Body | 2xx | Authz |
|---|---|---|---|---|
| POST | `/auth/login` | `LoginRequest{email,password}` | `200 TokenResponse{accessToken,refreshToken,expiresIn,user:UserSummary}` | public |
| POST | `/auth/refresh` | `RefreshRequest{refreshToken}` | `200 TokenResponse` | public |
| POST | `/auth/logout` | — | `204` | authenticated |
| GET | `/auth/me` | — | `200 CurrentUserResponse{id,email,fullName,roles[],permissions[],departmentId,applicantId}` | authenticated |

`401` on bad credentials, `403` on `BLOCKED`/`DISABLED` user.

## 2. Applicants

| Method | Path | Body | 2xx | Authz |
|---|---|---|---|---|
| POST | `/applicants` | `CreateApplicantRequest` | `201 ApplicantResponse` | `APPLICANT` (self) or `OPERATOR`/`ADMIN` with `USER_ADMIN:CREATE` |
| GET | `/applicants/{id}` | — | `200 ApplicantResponse` | owner, or staff with `APPLICATION:VIEW` |
| PATCH | `/applicants/{id}` | `UpdateApplicantRequest` | `200 ApplicantResponse` | owner (before first registration) or `ADMIN` |

`CreateApplicantRequest` is validated with **Bean Validation groups** (spec 15.2):
```
{ type: INDIVIDUAL|LEGAL_ENTITY,
  // group Individual: lastName, firstName, middleName?, birthDate, passportSeries,
  //                   passportNumber, pinfl
  // group LegalEntity: orgName, tin, representativeFullName, representativePosition,
  //                    powerOfAttorneyRef?
  address, phone, email }
```
Group selection is driven by `type` via a `@GroupSequenceProvider`. Wrong-type fields present →
`400 VALIDATION_FAILED` listing each offending field.

## 3. Applications

| Method | Path | Body | 2xx | Authz |
|---|---|---|---|---|
| POST | `/applications` | `CreateApplicationRequest{serviceId,submissionChannel,formData,items[]}` | `201 ApplicationResponse` | `APPLICANT` for own; staff with `APPLICATION:CREATE` for `PAPER`/`SINGLE_WINDOW` |
| POST | `/applications/{id}/submit` | — | `200 ApplicationResponse` | owner |
| POST | `/applications/{id}/register` | `RegisterApplicationRequest{note?}` | `201 CaseResponse` | staff with `APPLICATION:EDIT` (spec 1.3) |
| GET | `/applications` | — | `200 PageResponse<ApplicationSummary>` | applicant → own only; staff → scope-filtered |
| GET | `/applications/{id}` | — | `200 ApplicationResponse` | owner or scoped staff |

`register` is where spec 1.4 happens: number, case, stage rows, first activation, audit — one
transaction, `201` with `Location: /api/cases/{id}`.
`422 REQUIRED_DATA_MISSING` when route-mandatory fields or documents are absent (spec 15.3).
`409 ALREADY_REGISTERED` on replay without an idempotency key.

## 4. Cases

| Method | Path | Body | 2xx | Authz |
|---|---|---|---|---|
| GET | `/cases` | filters: `status,serviceId,departmentId,mode,overdue,stageCode,q` | `200 PageResponse<CaseSummary>` | scoped (§SECURITY 5) |
| GET | `/cases/{id}` | — | `200 CaseResponse` | scoped |
| GET | `/cases/{id}/timeline` | — | `200 List<StageTimelineItem>` | scoped, internal view |
| GET | `/cases/{id}/tracking` | — | `200 ApplicantTrackingResponse` | **applicant view**, external stages only |
| POST | `/cases/{id}/primary-check` | `PrimaryCheckRequest{category,decision,reason?,checklist?,newWorkflowId?}` | `200 CaseResponse` | `PRIMARY_CHECK:CREATE` + assigned task |
| GET | `/cases/{id}/items` | — | `200 List<CaseItemResponse>` | scoped |
| PUT | `/cases/{id}/items` | `List<CaseItemRequest>` | `200 List<CaseItemResponse>` | `CASE:EDIT`, only before price confirmation |
| POST | `/cases/{id}/comments` | `CreateCommentRequest{body,documentVersionId?}` | `201 CommentResponse` | staff with `CASE:VIEW` |
| GET | `/cases/{id}/comments` | — | `200 List<CommentResponse>` | **staff only — never the applicant** (spec 13.5.1, 17.8) |

`CaseResponse` (internal): `id, caseNumber, applicationNumber, applicant, service, status,
currentStage{code,name,internalStatusLabel}, activeStages[], primaryCheckCategory,
primaryCheckDecision, processingMode, workflow{code,version}, mainResponsibleDepartment,
participatingDepartments[], dueAt, paymentDueAt, paymentOverdue, finance{...}, version, createdAt`.

`ApplicantTrackingResponse` (external, spec 4.19, 15.5–15.7, 15.13) deliberately contains **only**:
`applicationNumber, submittedAt, serviceName, externalStage{code,nameForApplicant}, contract{number,
date,actualAmount,currency}, payment{status,confirmedAmount,debtAmount}, returnedForCorrection{reason,
remarks,dueDate}?, finalDocument{id,name,issuedAt}?, notifications[]`.
It never exposes internal stage names, executors, internal comments, the CRM-calculated price or
performed-works data. Enforced by a separate mapper and by test S-07, not by trusting the caller.

`400 INVALID_STATE_TRANSITION` when a command does not match the current stage.

## 5. Accounting

| Method | Path | Body | 2xx | Authz |
|---|---|---|---|---|
| POST | `/accounting/cases/{id}/processing-mode` | `SetProcessingModeRequest{mode}` | `200 CaseResponse` | `ACCOUNTANT` + `FINANCE:EDIT` |
| POST | `/accounting/cases/{id}/price/calculate` | — | `200 PriceCalculationResponse` | `FINANCE:CREATE` |
| GET | `/accounting/cases/{id}/price` | — | `200 PriceCalculationResponse` (with history) | `FINANCE:VIEW` |
| POST | `/accounting/cases/{id}/price/confirm` | `ConfirmPriceRequest{actualAmount?,reason?}` | `200 ContractResponse` | `FINANCE:APPROVE` |
| POST | `/accounting/cases/{id}/contract` | `RecordContractRequest{contractNumber,contractDate,sentChannel,sentAt,invoiceReference?,invoiceDate?}` | `200 ContractResponse` | `FINANCE:EDIT` |
| GET | `/accounting/cases/{id}/payment` | — | `200 PaymentResponse` | `FINANCE:VIEW` (applicant gets the trimmed view via `/cases/{id}/tracking`) |
| POST | `/accounting/cases/{id}/payment/confirm` | `ConfirmPaymentRequest{amount,externalReference?,note?}` | `200 PaymentResponse` | `FINANCE:APPROVE` |
| POST | `/accounting/cases/{id}/payment/status` | `SetPaymentStatusRequest{status,note}` | `200 PaymentResponse` | `FINANCE:APPROVE` |

Rules the endpoints enforce, each traceable to the spec:
* `403 APPLICANT_CANNOT_SET_MODE` — the applicant never chooses the mode (1.9, 4.3, 15.4).
* `422 EXPEDITED_NOT_ALLOWED` when `workflow.expeditedAllowed = false` (5.7).
* changing the mode **before** confirmation auto-creates a `MODE_CHANGED` recalculation (12.3); the
  response shows both calculations.
* `ConfirmPriceRequest` with no `actualAmount` confirms the calculated value; with a different
  `actualAmount` it **requires** `reason` and records `changedBy/changedAt` (12.4) — the original
  calculation is never overwritten.
* `422 PAYMENT_EXCEEDS_CONTRACT` when confirmations would exceed the contract amount.
* Confirming full payment triggers `PAYMENT_STATE_SATISFIED` and advances the case (12.8).

`PriceCalculationResponse{id,calculationNo,processingMode,calculatedTotal,currency,triggerReason,
status,calculatedAt,lines[{lineNo,description,quantity,unitPrice,coefficient,lineTotal,caseItemId}],
supersededHistory[],demoNotice}` — `demoNotice` literally says the tariffs are demonstration values.

## 6. Tasks

| Method | Path | Body | 2xx | Authz |
|---|---|---|---|---|
| GET | `/tasks` | filters `status,caseId,departmentId,assigneeId,overdue` | `200 PageResponse<TaskSummary>` | scoped |
| GET | `/tasks/my` | — | `200 PageResponse<TaskSummary>` | authenticated staff |
| GET | `/tasks/{id}` | — | `200 TaskResponse` | assignee, dept head, main-responsible, operator, head |
| POST | `/tasks/{id}/assign` | `AssignTaskRequest{userId}` | `200 TaskResponse` | **department head of that task's department only** (3.3, 5.5) |
| POST | `/tasks/{id}/reassign` | `AssignTaskRequest{userId,reason}` | `200 TaskResponse` | same |
| POST | `/tasks/{id}/start` | — | `200 TaskResponse` | assignee |
| POST | `/tasks/{id}/results` | `SubmitTaskResultRequest{payload,summary,revisionReason?}` | `201 TaskResultResponse` | assignee |
| GET | `/tasks/{id}/results` | — | `200 List<TaskResultResponse>` (full version chain) | scoped |
| POST | `/tasks/{id}/complete` | `CompleteTaskRequest{version}` | `200 TaskResponse` | assignee |
| POST | `/tasks/{id}/approve-result` | `ApproveResultRequest{comment?}` | `200 TaskResponse` | dept head (`TASK:APPROVE`) |
| POST | `/tasks/{id}/return` | `ReturnTaskRequest{reason}` | `200 TaskResponse` | dept head |

* `422 RESULT_REQUIRED` — completing a stage that `requiresResult` without a submitted result (7.5.7 principle).
* `409 RESULT_ALREADY_APPROVED` — attempting to overwrite an approved result instead of using `/return` (7.13).
* `400 REVISION_REASON_REQUIRED` — a return without a reason.
* replaying `/complete` on a `COMPLETED` task returns `200` with the same body and does **not** advance
  the workflow a second time.

## 7. Documents & approvals

| Method | Path | Body | 2xx | Authz |
|---|---|---|---|---|
| POST | `/cases/{id}/documents` | `CreateDocumentRequest{documentType,title}` | `201 DocumentResponse` | `DOCUMENT:CREATE` on the case |
| GET | `/cases/{id}/documents` | — | `200 List<DocumentSummary>` | scoped |
| GET | `/documents/{id}` | — | `200 DocumentResponse{...,versions[]}` | scoped |
| POST | `/documents/{id}/versions` | `CreateDocumentVersionRequest{contentRef,contentHash,fileName,mimeType,sizeBytes,fields,revisionReason?}` | `201 DocumentVersionResponse` | author/`DOCUMENT:EDIT` |
| GET | `/documents/{id}/versions/{versionNo}` | — | `200 DocumentVersionResponse` | scoped |
| POST | `/documents/{id}/versions/{versionNo}/approval-rounds` | `StartApprovalRequest{mode,participants[{kind,userId?,departmentId?,required,sequenceNo}]}` | `201 ApprovalRoundResponse` | `APPROVAL:ENDORSE` / route-configured initiator |
| GET | `/approval-rounds/{id}` | — | `200 ApprovalRoundResponse{...,tasks[]}` | participants + scoped staff |
| GET | `/approvals/my` | — | `200 PageResponse<ApprovalTaskSummary>` | authenticated (applicant sees own, spec 13.6) |
| POST | `/approval-tasks/{id}/approve` | `ApprovalDecisionRequest{comment?}` | `200 ApprovalTaskResponse` | that participant only |
| POST | `/approval-tasks/{id}/reject` | `ApprovalDecisionRequest{comment}` | `200 ApprovalTaskResponse` | that participant only |
| POST | `/documents/{id}/versions/{versionNo}/sign` | `SignRequest{note?}` | `200 DocumentVersionResponse` | **`HEAD_OF_CERTIFICATION_BODY` only** (14.4) |

* `400 APPROVAL_COMMENT_REQUIRED` on reject without a comment (13.4).
* `409 VERSION_ALREADY_UNDER_APPROVAL` — one open round per version.
* `422 APPROVAL_NOT_COMPLETED` — signing before all required participants approved (13.7).
* `422 DOCUMENT_VERSION_IMMUTABLE` — any attempt to PUT content onto an existing version. There is
  deliberately **no** `PUT /documents/{id}/versions/{n}` in this API.
* `403 SIGN_NOT_PERMITTED` — a department head or ADMIN attempting to sign (16.17).

## 8. Workflows, performed works, audit, admin

| Method | Path | 2xx | Authz |
|---|---|---|---|
| GET | `/workflows` | `200 List<WorkflowSummary>` (all versions, status) | `WORKFLOW_CONFIG:VIEW` |
| GET | `/workflows/{id}` | `200 WorkflowResponse{stages[],transitions[]}` | `WORKFLOW_CONFIG:VIEW` |
| POST | `/workflows/{code}/versions` | `201 WorkflowResponse` (draft copy) | `WORKFLOW_CONFIG:CREATE` |
| PATCH | `/workflows/{id}/stages` | `200 WorkflowResponse` | `WORKFLOW_CONFIG:EDIT`, **DRAFT only** |
| POST | `/workflows/{id}/publish` | `200 WorkflowResponse` | `WORKFLOW_CONFIG:EDIT` |
| POST | `/workflows/{id}/retire` | `200 WorkflowResponse` | `WORKFLOW_CONFIG:EDIT` |
| GET | `/performed-works` | `200 PageResponse<PerformedWorkResponse>` filters `caseId,executorId,departmentId,workTypeCode,from,to` | `PERFORMED_WORK:VIEW`, department-scoped |
| GET | `/cases/{id}/performed-works` | `200 List<PerformedWorkResponse>` | scoped |
| GET | `/audit` | `200 PageResponse<AuditEntryResponse>` filters `caseId,userId,action,entityType,from,to` | `AUDIT:VIEW` |
| GET | `/cases/{id}/audit` | `200 PageResponse<AuditEntryResponse>` | scoped + `AUDIT:VIEW` |
| GET | `/audit/integrity` | `200 AuditIntegrityResponse{intact,firstBrokenSeq?}` | `AUDIT:VIEW` |
| GET/POST/PATCH | `/admin/users`, `/admin/users/{id}/block`, `/admin/departments`, `/admin/positions`, `/admin/roles/{code}/permissions`, `/admin/services`, `/admin/work-types`, `/admin/external-stages`, `/admin/price-rules` | as usual | `ADMIN` + the matching `*:*` permission |

**There is no `POST /audit`, no `PATCH /audit/{id}`, no `DELETE /audit/{id}`.** By design (spec 20.3).
`PATCH /workflows/{id}/stages` on a non-DRAFT workflow → `409 WORKFLOW_VERSION_IMMUTABLE`.
`GET /admin/price-rules` and any endpoint returning contract sums, when called by `ADMIN`, writes a
`CONFIDENTIAL_DATA_ACCESSED` audit row (spec 16.18).

## 9. Error contract

```json
{
  "timestamp": "2026-08-27T13:40:11.482Z",
  "status": 422,
  "error": "UNPROCESSABLE_ENTITY",
  "code": "RESULT_REQUIRED",
  "message": "Task cannot be completed without a submitted result",
  "path": "/api/tasks/8f2.../complete",
  "traceId": "b7c1e9...",
  "details": [ { "field": "resultId", "issue": "must be present" } ]
}
```

| HTTP | When | Example codes |
|---|---|---|
| 400 | malformed / missing mandatory argument | `VALIDATION_FAILED`, `APPROVAL_COMMENT_REQUIRED`, `REVISION_REASON_REQUIRED` |
| 401 | missing/expired/invalid JWT | `UNAUTHENTICATED`, `TOKEN_EXPIRED` |
| 403 | authenticated but not permitted | `PERMISSION_DENIED`, `APPLICANT_CANNOT_SET_MODE`, `SIGN_NOT_PERMITTED`, `NOT_TASK_ASSIGNEE`, `DEPARTMENT_SCOPE_VIOLATION` |
| 404 | absent, **or hidden by ownership** | `RESOURCE_NOT_FOUND` |
| 409 | concurrency / duplicate / immutability | `CONCURRENT_MODIFICATION`, `ALREADY_REGISTERED`, `RESULT_ALREADY_APPROVED`, `WORKFLOW_VERSION_IMMUTABLE`, `IDEMPOTENCY_KEY_REUSED` |
| 422 | valid syntax, illegal business state | `INVALID_STATE_TRANSITION`, `RESULT_REQUIRED`, `EXPEDITED_NOT_ALLOWED`, `PARALLEL_TASKS_INCOMPLETE`, `APPROVAL_NOT_COMPLETED`, `PAYMENT_EXCEEDS_CONTRACT`, `DOCUMENT_VERSION_IMMUTABLE` |
| 500 | unexpected | `INTERNAL_ERROR` (message scrubbed, `traceId` kept) |

One `@RestControllerAdvice`. Domain exception hierarchy: `DomainException` →
`ValidationException(400)`, `AccessDeniedDomainException(403)`, `NotFoundException(404)`,
`ConflictException(409)`, `IllegalStateTransitionException(422)`.
`ObjectOptimisticLockingFailureException` → `409 CONCURRENT_MODIFICATION`.
`DataIntegrityViolationException` is translated by constraint name so that a DB guard surfaces as a
meaningful business code, not a 500.

## 10. Swagger

`springdoc-openapi`, UI at `/swagger-ui.html`, spec at `/v3/api-docs`. Bearer security scheme declared
globally; every endpoint documents its request DTO, response DTO, authorization requirement and its
possible error codes via `@ApiResponses`. Tags mirror the modules. The `PriceCalculationResponse` and
`WorkflowResponse` schemas carry an explicit description stating that tariffs, deadlines and
RED/YELLOW/GREEN criteria are demonstration assumptions.
