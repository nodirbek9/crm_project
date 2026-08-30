# TEST_MATRIX.md

**58 named tests.** Every row maps to a specification clause or to a `PLAN_REVIEW` correction. If a test
has no spec reference, it does not belong here.

Stack: JUnit 5, Mockito, AssertJ, `@SpringBootTest` + `MockMvc` for integration, **Testcontainers
PostgreSQL 16** for anything touching persistence. No H2 anywhere — half of these tests are *about*
constraints and triggers, which H2 does not have.

Layout:
```
src/test/java/.../unit/          plain JUnit + Mockito, no Spring context
src/test/java/.../integration/   @SpringBootTest + Testcontainers
src/test/java/.../security/      MockMvc with @WithMockUser-equivalent JWT helpers
src/test/java/.../workflow/      engine scenarios end to end
src/test/java/.../concurrency/   ExecutorService + CountDownLatch races
src/test/resources/              application-test.yml, fixtures
AbstractIntegrationTest          @Container static PostgreSQLContainer, @DynamicPropertySource
```
Container reuse (`.withReuse(true)`) + one static container for the whole suite; each test class gets a
clean schema via `@Sql` truncation rather than a new container. Target: full suite under 3 minutes.

---

## A. Unit tests (no DB, no Spring) — 14

| ID | Test | Spec |
|---|---|---|
| U-01 | `PrimaryCheckEvaluator` returns GREEN when all required fields and docs present | 4.5, **[DEMO A1]** |
| U-02 | returns YELLOW on missing optional data | **[DEMO A1]** |
| U-03 | returns RED on a missing required document | **[DEMO A1]** |
| U-04 | category and decision are produced as two independent values | 1.5, 4.6 |
| U-05 | `PriceCalculator` sums per-item lines × mode coefficient | 12.2, **[DEMO A3]** |
| U-06 | EXPEDITED coefficient produces a higher total than TRADITIONAL | 5.8, 12.2 |
| U-07 | `MINIMUM_TOTAL` rule raises a below-minimum total | **[DEMO A3]** |
| U-08 | calculation emits one line per `CaseItem` | 12.6 |
| U-09 | `DeadlineCalculator` halves the deadline for EXPEDITED, floor 1 day | 5.8, **[DEMO A5]** |
| U-10 | `TransitionEvaluator` `ALL_REQUIRED_PARALLEL_TASKS_DONE` ignores optional siblings | 7.14 |
| U-11 | `TransitionEvaluator` `PAYMENT_STATE_SATISFIED` false on PARTIALLY_PAID when the route forbids early start | 12.8 |
| U-12 | same evaluator true on PARTIALLY_PAID when the route allows it | 12.8 |
| U-13 | `ContractAmountBracketResolver` boundary behaviour at exactly 10/20/30M | 8.4, **[DEMO A4]** |
| U-14 | `ExternalStageMapper` maps three internal stages onto one external stage | 5.11, 15.6 |

## B. Integration tests — core flow — 15

| ID | Test | Spec |
|---|---|---|
| I-01 | registering an application creates exactly one `ElectronicCase`, a case number and one `case_stage` row per workflow stage | 1.4, 4.4 |
| I-02 | registration pins `workflow_id` to the ACTIVE version and activates the entry stage | 5.12 |
| I-03 | INDIVIDUAL applicant missing `pinfl` → `400 VALIDATION_FAILED`; the DB CHECK also rejects a direct insert | 15.2 |
| I-04 | LEGAL_ENTITY applicant missing `tin` → `400`; individual-only fields on a legal entity → `400` | 15.2 |
| I-05 | PAPER submission without a registrar is rejected; with a registrar it succeeds | 1.3 |
| I-06 | primary check writes category and decision separately and both persist | 1.5, 4.6 |
| I-07 | decision `RETURNED_TO_APPLICANT` moves the case to RETURNED and shows reason + remarks in the applicant tracking view | 4.7, 15.8 |
| I-08 | decision `ROUTE_CHANGED` rebinds the case to the named workflow and audits it | 4.7 |
| I-09 | decision without a reason → `400` (DB CHECK also rejects) | 4.7 |
| I-10 | accountant sets EXPEDITED; case records who and when | 1.9, 12.1 |
| I-11 | price is calculated with one line per item and a stored rule snapshot | 12.2, 12.6 |
| I-12 | confirming a different actual amount preserves `calculated_amount`, records `changed_by` + `changed_at`, and requires a reason | **12.4** |
| I-13 | contract recording requires number, date, channel and `sent_at` together | 12.5 |
| I-14 | full payment confirmation advances the case out of WAITING_PAYMENT | 12.8 |
| I-15 | partial payment keeps `debt = contract − confirmed` and status PARTIALLY_PAID | 12.7 |

## C. Workflow engine tests — 12

| ID | Test | Spec |
|---|---|---|
| W-01 | sequential route advances stage by stage to COMPLETION | 4.2 |
| W-02 | completing IDENTIFICATION activates all three parallel stages | 7.14, 4.13 |
| W-03 | `current_stage_id` is NULL while a parallel group is open, and the applicant still sees one external stage | 5.11, 15.6 |
| W-04 | a task cannot complete without a required result → `422 RESULT_REQUIRED` | 7.5.7 principle |
| W-05 | **one incomplete required parallel task blocks the dependent stage** | **7.14** |
| W-06 | an incomplete *optional* parallel task does **not** block it | 7.14 |
| W-07 | completing the last required sibling activates FINAL_REVIEW exactly once | 7.14 |
| W-08 | returning a task to revision reopens the stage without creating a second stage row | 7.13 |
| W-09 | a new result version supersedes the old one, and the old payload stays readable | 7.13 |
| W-10 | attempting to overwrite an APPROVED result → `409 RESULT_ALREADY_APPROVED`; the DB trigger also rejects it | 7.13 |
| W-11 | **a case registered on workflow v1 completes normally after v2 is published with a stage removed** | **5.12, 16.11** |
| W-12 | `PATCH` on a published (non-DRAFT) workflow → `409 WORKFLOW_VERSION_IMMUTABLE` | 16.11 |

## D. Document & approval tests — 8

| ID | Test | Spec |
|---|---|---|
| D-01 | creating a second document version supersedes the first; both remain retrievable with their hashes | 6.6, 13.5 |
| D-02 | a direct `UPDATE` of `document_version.content_ref` is rejected by the trigger | **13.5** |
| D-03 | an endorsement round fans out one `ApprovalTask` per participant | **13.3** |
| D-04 | rejecting without a comment → `400`; the DB CHECK also rejects it | **13.4** |
| D-05 | with two required participants, one approval does **not** complete the round | **13.7** |
| D-06 | all required approvals complete the round, mark the version ENDORSED and advance the case | 13.7 |
| D-07 | a rejection produces a new version and a **new** round; remarks stay bound to the old version | 13.4, 13.5 |
| D-08 | signing before the round completes → `422 APPROVAL_NOT_COMPLETED`; a signed version cannot change status | 13.7, 14.6 |

## E. Security tests — 12

| ID | Test | Spec |
|---|---|---|
| S-01 | no token → `401`; expired token → `401 TOKEN_EXPIRED` | — |
| S-02 | a BLOCKED user is rejected at the filter | 16.3 |
| S-03 | **applicant cannot set the processing mode → `403 APPLICANT_CANNOT_SET_MODE`** | **1.9, 4.3, 15.4** |
| S-04 | accountant can set it; EXPEDITED on a route that forbids it → `422` | 5.7, 12.1 |
| S-05 | **applicant reading another applicant's case → `404`, not `403`** | **3.1** |
| S-06 | applicant listing applications sees only their own | 3.1 |
| S-07 | **the applicant tracking payload contains no internal stage, executor, internal comment, calculated price or performed-work field** (asserted on raw JSON) | **15.9, 15.13, 4.19** |
| S-08 | specialist acting on a task assigned to another department → `403 NOT_TASK_ASSIGNEE` | 3.4, 7.5.6 |
| S-09 | a department head cannot assign a task belonging to another department | 3.3, 5.5 |
| S-10 | **only `HEAD_OF_CERTIFICATION_BODY` can sign; department head and ADMIN get `403`** | **14.4, 16.17** |
| S-11 | **changing a user's position does not change their effective permissions** | **16.13** |
| S-12 | ADMIN reading price rules produces a `CONFIDENTIAL_DATA_ACCESSED` audit row | **16.18** |

Extra guard: `ACCOUNTANT` calling any `/tasks/**` mutating endpoint → `403` (spec 3.6, "не изменяет
экспертные и исполнительные результаты"); `OPERATOR` calling payment confirm → `403` (spec 17.5).

## F. Concurrency & idempotency tests — 7

Pattern: `ExecutorService` with N threads + `CountDownLatch` so both requests hit the same row inside
the same window, then assert on final DB state — never on which thread won.

| ID | Test | Spec |
|---|---|---|
| C-01 | **two simultaneous `completeTask` calls: the task completes once and the workflow advances once** | plan §19 |
| C-02 | **two parallel siblings finishing simultaneously activate the dependent stage exactly once** (`activation_count = 1`) | 7.14 |
| C-03 | two simultaneous `confirmPayment` with the same `externalReference`: one succeeds, one is rejected; `confirmed_amount` is correct | 12.7 |
| C-04 | a stale `PATCH` (old `version`) → `409 CONCURRENT_MODIFICATION` | plan §19 |
| C-05 | a replayed `POST /applications/{id}/register` with the same `Idempotency-Key` returns the stored response and creates one case | 1.4 |
| C-06 | two simultaneous approval decisions closing the round: the round closes once | 13.7 |
| C-07 | two simultaneous `publish` on the same workflow code: one wins on the partial unique index | 16.11 |

## G. Audit & performed-works tests — 8

| ID | Test | Spec |
|---|---|---|
| A-01 | every command in the happy path writes its expected `AuditAction`; the full demo scenario produces the documented sequence | 1.19 |
| A-02 | **`UPDATE audit_log` and `DELETE FROM audit_log` both raise, as `crm_app` and as owner** | **20.3** |
| A-03 | `TRUNCATE audit_log` raises | 20.3 |
| A-04 | an administrative event (role permission change) is stored with `case_id IS NULL` and old/new values | **16.10, C4** |
| A-05 | `verify_audit_chain()` reports no break after the full demo run, and reports the exact `seq` after a forced tamper | 20.3 |
| PW-01 | completing a stage with a `work_type` records exactly one `PerformedWork` with executor, department, mode and supporting document | **8.1, 8.3** |
| PW-02 | **a revision cycle does not create a second `PerformedWork`; the existing row's supporting document is refreshed** | **8.5** |
| PW-03 | `GREEN_DOC_EXPERTISE` without a contract-amount bracket is rejected; a non-green work type carrying one is also rejected | **8.4** |

## H. Non-functional / build checks — 2

| ID | Check |
|---|---|
| N-01 | Flyway migrates from empty to head on a fresh container, and the JPA schema validates against it (`ddl-auto: validate`) — catches every entity/migration drift |
| N-02 | `docker compose up` brings up postgres + backend; `/actuator/health` returns UP and `/v3/api-docs` is served |

## Coverage intent

Not a percentage target. The rule: **every CRITICAL and HIGH item in `PLAN_REVIEW.md` has at least one
test that fails if the correction is reverted.**

| PLAN_REVIEW item | Guarded by |
|---|---|
| C1 DocumentVersion mandatory | D-01, D-02, D-07 |
| C2 two manager roles | S-09, S-10 |
| C3 per-participant approval | D-03, D-05, D-06, C-06 |
| C4 nullable audit case_id + DB immutability | A-02, A-03, A-04 |
| H1 external stages N:1 | U-14, W-03, S-07 |
| H2 pricing item composition | U-08, I-11 |
| H3 performed works | PW-01, PW-02, PW-03 |
| H4 five primary-check decisions | I-06, I-07, I-08, I-09 |
| H5 recalc / waiting period / early execution | U-11, U-12, I-10, I-14 |
| H6 applicant type | I-03, I-04 |
| H7 submission channel | I-05 |
| M1 status vs stage | W-03 |
| M2 seven permissions | S-11, S-12 |
| M3/M4 position, reassignment | S-09, S-11 |
| M8 confidential access logging | S-12 |
