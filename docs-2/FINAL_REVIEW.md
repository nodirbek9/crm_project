# Final Review — Independent Senior Reviewer Pass

Date: 2026-08-30. Scope: the complete `crm-backend` codebase at the end of Phase 13, reviewed as
if by a senior engineer seeing it for the first time, not by the author of any given phase. Every
CRITICAL and HIGH finding below was fixed before this document was closed; the full suite
(`./mvnw clean verify`) is green at 157/157 after every fix.

## Method

Not a re-read of every file. Three passes, each chosen because it is where real bugs actually
hide in a codebase like this one: (1) every port interface (`.../port/*.java`) cross-checked
against its implementations — a missing bean fails silently by design (the gate stays shut), which
is exactly the kind of gap code review alone does not catch; (2) a genuine, automated, end-to-end
walk of the documented demo scenario through the real HTTP API, not a unit test of one method in
isolation; (3) a grep sweep for `TODO`/`FIXME`/stub markers and for every `@PreAuthorize` using
`isAuthenticated()` where a specific permission was clearly intended.

## CRITICAL

### C1. `ApprovalStateProvider` was never implemented — `ENDORSEMENT → SIGNING` could not fire

**Found by:** writing `DemoScenarioWalkthroughTest`, an automated version of the demo script in
`docs-2/FINAL_IMPLEMENTATION_ORDER.md`, driven through the real API with the real demo seed data.
The case reached `ENDORSEMENT`, the round completed correctly (both participants approved, the
document version was marked `ENDORSED`), and then the case simply stopped advancing — `SIGNING`
never activated.

**Root cause:** `ApprovalRoundCompletedConditionHandler` (Phase 7) asks an `ApprovalStateProvider`
bean whether the round is done; with none present it correctly defaults to "not satisfied"
(`docs-2/ASSUMPTIONS.md` A25, an honest placeholder). Phase 10 (documents/approval) never actually
wrote that bean. Every existing test of this transition (`DocumentApprovalIntegrationTest`) used
`ConditionType.ALWAYS` for its own test route instead of the real `APPROVAL_ROUND_COMPLETED`
condition the demo route uses — so the gap was invisible to the suite for three phases.

**Impact:** the documented demo scenario — and any real case using a `PARALLEL` or `SEQUENTIAL`
endorsement round — could never reach `SIGNING` through the API. A case would sit at `ENDORSEMENT`
forever after every participant approved, with no error and no indication anything was wrong.
This is as severe as a bug gets in a certification workflow: work that is genuinely done never
gets recognised as done.

**Fix:** `uz.ithunter.crm.approval.CaseApprovalStateProvider` now implements the port —
`existsByElectronicCaseIdAndStatus(caseId, COMPLETED_APPROVED)`, a new repository method with its
own reasoning documented on `ApprovalRoundRepository` (a rejected round stays
`COMPLETED_REJECTED` forever; a revision opens a new round, so "any `COMPLETED_APPROVED` round for
this case" is correct even across a reject → revise → re-approve cycle without needing to
correlate rounds to a specific workflow-stage row). Verified by re-running
`DemoScenarioWalkthroughTest` to a `COMPLETED` case, and by the full suite staying green.

## HIGH

None open. Three HIGH-severity issues were found and fixed during earlier phases of this same
project (Phase 10's `DocumentController` and Phase 11's `PerformedWorkController` each had a GET
endpoint gated by `isAuthenticated()` instead of the specific permission spec 15.13 requires,
letting a role see data it should not; Phase 12 found two genuine PostgreSQL deadlocks and one
audit-hash-chain race condition under real concurrent load). All are recorded with full detail in
`docs-2/IMPLEMENTATION_STATUS.md`'s per-phase sections and are not repeated here — this document
covers what was still open going into Phase 13, not the project's whole history.

## MEDIUM

### M1. The demo/production bootstrap step is easy to miss

Registering a case requires `APPLICATION:EDIT`, which the specification's own permission table
(and therefore the seeded matrix) grants to no role — `docs-2/ASSUMPTIONS.md` A30 documents this
as intentional, not an oversight. A fresh deployment is unusable until an admin grants it to
`DEPARTMENT_HEAD` through `PATCH /api/admin/roles/DEPARTMENT_HEAD/permissions`. This is now
documented as an explicit "step 0" in the README and exercised (and reverted) automatically by
`DemoScenarioWalkthroughTest`, so it will not be missed by anyone following either document — but
a real product would want this seeded by default or surfaced as a first-run wizard rather than
left as a manual API call.

### M2. `IdempotencyFilter`'s claim-then-store is not atomic against a genuinely simultaneous
replay with the same key

Documented deliberately in `docs-2/ASSUMPTIONS.md` A45: TEST_MATRIX.md's C-05 is a sequential
replay, which the current implementation handles correctly. Two truly simultaneous requests with
the identical `Idempotency-Key` could both proceed to business logic before either's `command_log`
row is visible to the other; the `uq_command_log_key` constraint is the real backstop in that
case, and the business-level idempotency (unique constraints, state pre-checks) that already exist
independently of this filter prevent any double side-effect. Worth a `SELECT ... FOR UPDATE`-based
claim if this endpoint set grows to include something without its own domain-level duplicate
guard.

## LOW

### L1. `docker compose up` could not be verified in this environment

The Docker image built successfully (`crm-backend-backend:latest`), but `postgres:5432` was
already bound by something outside this project's containers in the sandbox this review ran in,
so the compose stack itself was not observed reaching a healthy state end-to-end. This is an
environment condition, not a code defect: the identical Flyway migrations, demo seed data, and
Spring Boot application are exercised — successfully, repeatedly — by the Testcontainers-backed
integration suite (`AbstractIntegrationTest`, same PostgreSQL 16 image family) and specifically by
`DemoScenarioWalkthroughTest`'s full walkthrough. Re-run `docker compose up --build` on a clean
machine to confirm N-02 directly; nothing found during this review suggests it would behave
differently there.

## Confirmed clean (checked, not just assumed)

- Every `@Version`-bearing DB column has a matching JPA `@Version` field and vice versa (12/12,
  Phase 12).
- Every port interface in `.../port/*.java` now has exactly one Spring-managed implementation.
- No `TODO`/`FIXME`/stub markers remain in `src/main/java`.
- No JPA entity is returned from a controller (every controller method returns a DTO record).
- `SecurityGrantMatrixTest` asserts the seeded 7×13 permission matrix exactly matches
  `SECURITY_SPEC.md` §3 — this review did not re-derive that matrix by hand, it trusted the test,
  which is itself a check worth naming: the matrix has not silently drifted since Phase 3.

## Outcome

157/157 tests green after C1's fix. No CRITICAL or HIGH findings remain open.
