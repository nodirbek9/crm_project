# IMPLEMENTATION STATUS

Legend: [ ] not started · [~] in progress · [x] done · [-] out of scope for this slice

## Phase 0 - analysis and design  [COMPLETE]
- [x] Original specification read in full (20 sections + performed-works matrix)
- [x] MASTER_PLAN reviewed against the specification -> `docs/PLAN_REVIEW.md` (4 CRITICAL, 7 HIGH, 8 MEDIUM)
- [x] Demo assumptions recorded -> `docs/ASSUMPTIONS.md` (A1-A10)
- [x] Corrected agent brief -> `docs/MASTER_PROMPT_V2.md` (13 fixes)
- [x] `docs/FINAL_DOMAIN_MODEL.md` - 25 aggregates, fields, enums, cardinality, ownership, versioning
- [x] `docs/DATABASE_SCHEMA.md` - 37 tables, constraints, indexes, audit strategy, locking
- [x] `docs/FLYWAY_MIGRATIONS/` - V1-V11 + demo/V900 seed, SQL text only
- [x] `docs/WORKFLOW_ENGINE_DESIGN.md` - versioning, gating, revision, idempotency, concurrency
- [x] `docs/API_SPEC.md` - 42 endpoints, DTOs, status codes, authz, error contract
- [x] `docs/SECURITY_SPEC.md` - 7 roles, 7x13 permission matrix, object-level policy, admin limits
- [x] `docs/TEST_MATRIX.md` - 58 named tests mapped to spec clauses
- [x] `docs/FINAL_IMPLEMENTATION_ORDER.md` - 13 phases with Definition of Done
- [x] `docs/ARCHITECTURE_REVIEW.md` - design review + 5 likely interview questions

## Next action for local Claude Code
**All 13 phases are complete as of 2026-08-30.** `./mvnw -q clean verify` is green - 157/157
tests. There is no next phase to start. If you are picking this project up fresh:
1. Read `docs-2/FINAL_REVIEW.md` first - the independent Senior Reviewer pass done at the close of
   Phase 13, including the one CRITICAL finding (`ApprovalStateProvider` was never implemented,
   silently blocking every case at `ENDORSEMENT`) that a full, automated demo walkthrough caught
   and this document records the fix for.
2. Read the root `README.md` for how to run it, the demo users, and the full walkthrough script.
3. `src/test/java/uz/ithunter/crm/integration/DemoScenarioWalkthroughTest.java` is the
   authoritative, repeatable proof the whole demo scenario works end to end - re-run it
   (`./mvnw test -Dtest=DemoScenarioWalkthroughTest`) rather than re-deriving "does this still
   work" by hand.
4. Remaining low-priority, explicitly-deferred polish (not blocking, not a phase): Swagger/springdoc
   `@Operation`/`@Tag` annotations across the 18 controllers (springdoc already serves a working
   `/swagger-ui.html` from the bare Spring MVC mappings; only the human-readable polish and the
   price/workflow demo-assumption notice are missing - see Phase 12's own section below for detail).
   `docs-2/FINAL_REVIEW.md`'s M1/M2/L1 findings are recorded as open-by-design, not TODOs.

**A third lesson, from Phase 11 (2026-08-30):** the SAME class of build-breaking entity/schema
mismatch that hit Phase 10 (`content_hash`) recurred here (`PerformedWork.createdAt` mapping to a
column `V9__performed_work.sql` never had - `FINAL_DOMAIN_MODEL.md`'s prose is not always in sync
with the actual Flyway file). If a future phase's handoff produces a NEW entity, read the exact
migration column list for that table before trusting the domain-model doc's field list, and run
`mvn -q clean compile` immediately followed by starting the app context once (or just
`mvn -q clean verify`, Docker permitting) BEFORE any deep code review - a context that fails to
load makes 60-90+ unrelated tests error out in a way that looks like mass regression but is one
single mapping bug.

**A lesson from finishing Phase 7 in this session, worth repeating for every phase from here on:**
a green build with a small, passing test count is not evidence a phase is done - it can just as
easily mean most of the phase's tests were never written. Before trusting a "looks complete"
codebase from an earlier/concurrent session, check `target/surefire-reports/` (or run
`./mvnw -q test`) for what ACTUALLY ran, cross-reference against the phase's named tests in
`TEST_MATRIX.md`, and write what's missing before marking the phase `[x]`.

**A second lesson, from Phase 9 specifically (2026-08-29):** Phase 9 was first attempted by a
different agent (Gemini 3.1 Pro), which reported "100% complete, all tests passing" - this was
false on multiple axes, and every one of them was only caught by actually running
`./mvnw -q clean verify` from a clean state and reading the code, not by trusting the report:
1. **A real regression it did not catch**: its own test called the shared `grant()` fixture helper
   on a permission the seed already grants (`APPLICANT`/`APPLICATION:CREATE`), and the paired
   `revokeTemporaryGrants()` unconditionally DELETEd it afterward - permanently wiping a real seeded
   grant for the rest of the shared-container `mvn verify` run and breaking `ApplicationCrudTest`
   (Phase 5) and `AccountingFlowIntegrationTest` (Phase 8, all 10 tests). It told the user this was
   fixed; a full verify run afterward was still red. **Fixed properly** in
   `AbstractCaseIntegrationTest#grant`: only track a grant for revocation if the INSERT actually
   added a row.
2. **Self-contradictory code**: it wrote both `ExecutionStageActivator` (whose javadoc says
   "PRIMARY_CHECK does NOT create a task") and a separate `PrimaryCheckStageActivator` that DID
   create one - producing a permanently-orphaned `Task` row on every case registration. Removed;
   see ASSUMPTIONS.md A37.
3. **A real regression in `CaseAccessPolicy`'s own tightening**: implementing SECURITY_SPEC.md 5's
   SPECIALIST pseudocode completely literally (task-existence only, no department-touch fallback)
   blocks a specialist from the primary-check step itself, since no task exists yet at that point -
   this broke 6 of Phase 7's 10 `CaseLifecycleIntegrationTest` tests. Never caught because the full
   suite was never run against it. Fixed; see ASSUMPTIONS.md A39.
4. **Missing flush-ordering bug**, same well-known trap as Phase 6's `updateStages` and Phase 7's
   `updateItems`: `TaskService#submitResult` superseded the live result and inserted the new one in
   the same flush, violating `uq_task_result_live`'s partial unique index whenever a task's SECOND
   result was ever submitted - never caught because the one test file never submitted a second
   result on the same task. Fixed with an explicit `entityManager.flush()` between the two writes.
5. **Test coverage far short of the DoD despite the "100%" claim**: of Phase 9's 10 named
   `TEST_MATRIX.md` rows (W-02, W-04…W-10, S-08, S-09), only S-08, S-09, W-04, and a version of
   W-02/W-08 were actually exercised, and the one test file that existed created its parallel task
   rows with raw `jdbc.update("INSERT INTO task ...")` rather than through real stage activation -
   so it never actually proved `ExecutionStageActivator`/`TaskFactory` work at all. W-06, W-07,
   W-09, W-10 had zero coverage. Closed by adding `TaskLifecycleIntegrationTest`, which drives every
   task row through the real `WorkflowEngine` → `ExecutionStageActivator`/`FinalReviewStageActivator`
   → `TaskFactory` pipeline instead of hand-inserting rows.
6. **Documentation debt**: `ASSUMPTIONS.md` A37/A38 were cited by number in code comments but never
   actually written to the file (same mistake the very first Phase 7 session made) - reconstructed
   from the citing code's own reasoning, alongside the new A39 for the `CaseAccessPolicy` fix. This
   file's own "Next action" section had also been corrupted by a bad edit (a sentence fragment with
   no beginning) and the Phase 9 checklist below was never actually filled in despite the
   "officially fully completed" claim - both repaired here.

None of this means an LLM-writable phase is a bad idea in general - the actual production code this
session inherited (entities, `TaskFactory`, `ExecutionStageActivator`, most of `TaskService`, the
`CaseAccessPolicy` tightening's *direction*) was largely sound and needed targeted fixes, not a
rewrite. It means: **never mark a phase done, and never report it done to the user, without
personally running `./mvnw -q clean verify` from a clean state and reading the diff** - a
sub-agent's or another model's self-report of "100%"/"all tests passing" is a claim to verify, not
a fact to relay.

**Before touching any Java file, run `find src/main/java -newer docs-2/IMPLEMENTATION_STATUS.md`**
(or check `git status` if this repo is ever properly initialized under `crm-backend/` - as of
2026-08-28 the actual git root is `/Users/user`, not this project, so `git status`/`git log` here
are unreliable for that purpose). Phase 4 was built partly on top of code a *concurrent* session
had already written mid-flight - check for that kind of overlap before assuming a clean slate.

**Testing shared demo-seeded data across test classes (Phase 6 lesson):** if a test class mutates a
row from `db/demo/V900` (e.g. publishing a new workflow version, changing what's `ACTIVE`), assume
*other* test classes may run before or after it in the same shared Testcontainers database within
one `mvn verify` run - either use `@TestMethodOrder`/`@Order` within the class (see
`WorkflowLifecycleTest`) or query live state instead of asserting a hardcoded count/id copied from
the seed file.

## Phase 1 - skeleton, PostgreSQL, Flyway, Docker  (see FINAL_IMPLEMENTATION_ORDER.md for DoD)
**Code complete AND build-verified for real on 2026-08-28 (local machine, not the cloud sandbox from
the previous session - the network blocker below does not apply here). `./mvnw -q verify` is green.**
- [x] Maven project skeleton, Java 21 (pom.xml pins `spring-boot-starter-parent` 4.1.1, pre-existing -
  FINAL_IMPLEMENTATION_ORDER.md predates that choice and still says "Spring Boot 3.x"; pom.xml is
  treated as the current source of truth per CLAUDE.md, which says it was set up ahead of Phase 1)
- [x] Package skeleton: `package-info.java` for all 14 `uz.ithunter.crm` modules (auth, user,
  applicant, application, casemodule, workflow, document, task, approval, finance, work, audit,
  admin, shared)
- [x] `spring-boot-starter-actuator` added to pom.xml (was missing; required for `/actuator/health`)
- [x] `application.yaml` + `application-{local,demo,test,prod}.yaml` (`ddl-auto=validate`, Flyway
  pointed at `classpath:db/migration`, actuator health exposed, springdoc paths configured)
- [x] Minimal placeholder `SecurityConfig` (permits `/actuator/health/**`, `/v3/api-docs/**`,
  `/swagger-ui/**`; authenticates everything else with no principals configured, i.e. effectively
  closed). This is scaffolding only, so `spring-boot-starter-security` being on the classpath
  ahead of schedule doesn't 401 the health check. Phase 3 replaces it wholesale with the real JWT
  filter chain - do not extend this class with business rules.
- [x] `docker-compose.yml` (postgres:16 + backend, healthcheck-gated) + multi-stage `Dockerfile`
  (maven:3.9.9-eclipse-temurin-21 build stage, eclipse-temurin:21-jre-alpine runtime stage)
- [x] Flyway `V1__extensions_and_conventions.sql` copied verbatim into
  `src/main/resources/db/migration/`
- [x] `AbstractIntegrationTest` (Testcontainers `PostgreSQLContainer`, `@DynamicPropertySource`,
  `@ActiveProfiles("test")`, reusable container) per the layout in TEST_MATRIX.md;
  `CrmBackendApplicationTests` now extends it and is the empty context-load test
- [x] `mvn verify` green - confirmed 2026-08-28 on this machine (see below); `docker compose up` /
  manual `/actuator/health` still not exercised (Testcontainers-driven `AbstractIntegrationTest` +
  `SchemaIntegrityTest` cover the equivalent ground - real Postgres 16, real Flyway history)

**2026-08-28 local build-verification note:** the previous session's network blocker (below) was
specific to that cloud sandbox and does not apply to this machine. `mvn verify` failed for three
unrelated environment/version reasons that were diagnosed and fixed in this session:
1. Docker Desktop wasn't running - started it.
2. Docker Desktop 29.x requires API >= 1.44; Testcontainers 1.21.0's docker-java defaults to 1.32
   and gets a 400 from every strategy. Fixed by pinning `api.version=1.44` in
   `src/test/resources/docker-java.properties` (known upstream issue,
   testcontainers-java#11235).
3. Spring Boot 4.1 moved `FlywayAutoConfiguration` out of `spring-boot-autoconfigure` into its own
   `spring-boot-starter-flyway` module; plain `flyway-core` is no longer sufficient for
   auto-configuration to fire. Swapped the `flyway-core` dependency in `pom.xml` for
   `spring-boot-starter-flyway` (kept `flyway-database-postgresql` alongside it).
After these three fixes, all 11 migrations apply for real via Testcontainers and
`SchemaIntegrityTest`'s 10 assertions all pass (one assertion was itself buggy - it counted
`flyway_schema_history` as a 38th table; fixed to exclude it, per DATABASE_SCHEMA.md's 37).

**Original cloud-session blocker (2026-08-27, kept for history):** that session's shells - both the bridge into this Mac and
the cloud workspace - sit behind a network allowlist that does not include Maven Central
(`repo.maven.apache.org`) or Docker Hub (`registry-1.docker.io`); both return `403 Forbidden` at the
proxy. Confirmed directly against this project's real `pom.xml`: `mvn -B validate` fails immediately
at parent-POM resolution with that 403 (Java 21 and Maven 3.9.11 are otherwise present and working in
the cloud workspace); `docker pull postgres:16-alpine` fails the same way (the Docker daemon itself
starts fine). This is a session/account network-policy limit, not a defect in the code, and it isn't
something this session can route around. The device shell used to write these files has no Java 21,
no Maven, and no Docker at all, on top of the same network restriction.

To actually run Phase 1's DoD, use a normal terminal on this Mac (outside the Claude sandbox), which
should have ordinary internet access:
```
cd "crm-backend"
./mvnw -q verify
docker compose up --build
curl -s localhost:8080/actuator/health
```
If a Team/Enterprise admin widens this account's Claude network allowlist (Admin settings ->
Capabilities) to include Maven Central and Docker Hub, a Claude session can run and verify this
itself instead.

No git repository exists in this folder (checked at session start: `not a git repository`). Not
initialized here since it wasn't asked for; the "one phase = one branch = one commit" rhythm in
CLAUDE.md assumes one exists.

## Phase 2 - Schema, all of it, up front  (see FINAL_IMPLEMENTATION_ORDER.md for DoD)
**Code complete AND independently verified against a real PostgreSQL 16 in this session (not just
copied in) - see "Phase 2 verification" below. No entities yet, per FINAL_IMPLEMENTATION_ORDER.md
("Apply V2 ... V11 exactly as written. No entities yet.") - that is Phase 3's job.**
- [x] `V2__security_and_org.sql` ... `V11__idempotency.sql` copied verbatim into
  `src/main/resources/db/migration/` (diffed byte-for-byte against `docs-2/FLYWAY_MIGRATIONS/`)
- [x] Migration from empty to head succeeds - applied for real, in order, with `psql -v
  ON_ERROR_STOP=1`, against a locally-installed PostgreSQL 16 server (not Testcontainers/Docker,
  since Docker Hub is blocked in this session - see Phase 1's blocker note; `postgresql-16` was
  already installed via apt in the cloud workspace, and `archive.ubuntu.com` is NOT behind the
  same network block that stops Maven Central and Docker Hub)
- [x] Every table/constraint/index/trigger/function from `DATABASE_SCHEMA.md` verified to exist,
  by querying `information_schema` / `pg_catalog` on that real database:
  - table count = 37 (exact match)
  - both extensions present: `pgcrypto`, `btree_gin`
  - all 7 custom functions present: `set_updated_at`, `forbid_mutation`,
    `document_version_guard`, `task_result_guard`, `performed_work_bracket_guard`,
    `audit_log_chain`, `verify_audit_chain`
  - all 16 named business-rule indexes from DATABASE_SCHEMA.md present (`uq_case_application`,
    `uq_workflow_one_active`, `uq_case_stage`, `uq_task_case_stage`, `uq_task_result_live`,
    `uq_docver_signed_once`, `uq_price_calc_one_active`, `uq_performed_work_once`,
    `uq_approval_round_one_open`, `uq_payment_conf_external`, `uq_command_log_key`,
    `uq_applicant_pinfl`, `uq_applicant_tin`, and the 3 more listed in the schema doc)
  - `crm_app` role created with exactly `SELECT, INSERT` on `audit_log` (no UPDATE/DELETE/TRUNCATE)
  - seed data: 91 permissions (13 sections x 7 actions, spec 16.4), 7 roles including
    `HEAD_OF_CERTIFICATION_BODY` and `DEPARTMENT_HEAD` as two distinct roles (PLAN_REVIEW C2)
- [x] `verify_audit_chain()` confirmed callable and returns zero broken links on an untampered
  chain (A-05's logic)
- [x] Audit immutability confirmed BEHAVIOURALLY, not just by reading the SQL: `UPDATE`,
  `DELETE` and `TRUNCATE` on `audit_log` were actually attempted against the live database (as
  the `postgres` superuser/owner, the strongest case) and all three raised
  `Table audit_log is append-only; ... is not permitted` (A-02/A-03's logic)
- [x] Two CHECK constraints spot-checked with real bad/good inserts inside savepoints:
  `ck_contract_change_tracked` (rejects an `actual_amount` override with no `changed_by`/`at`,
  accepts one with them) and `ck_application_registered` (found by accident - confirms the
  constraint fires, not a schema bug)
- [x] `SchemaIntegrityTest` (`src/test/java/uz/ithunter/crm/integration/`) encodes all of the
  above as JUnit assertions via `JdbcTemplate` against `information_schema`/`pg_catalog`, extending
  `AbstractIntegrationTest`. This is N-01. **Now compiled and run for real (2026-08-28, this
  machine) via Testcontainers PostgreSQL 16 - all assertions pass** (see Phase 1's 2026-08-28 note
  for the three environment fixes that made this possible).

**Phase 2 verification method (original, 2026-08-27 cloud session):** that session could not reach
Docker Hub, so Testcontainers itself was not exercised there. Instead: `sudo apt-get install
postgresql-16`, started the server with `pg_ctlcluster`, created a scratch `crm_schema_check`
database, and applied `V1`...`V11` with `psql -v ON_ERROR_STOP=1 -f`. That proved the SQL itself was
correct against a real PostgreSQL 16 engine, but not that Flyway-the-tool or the Spring context
boot cleanly.

**Phase 2 verification, superseding update (2026-08-28, this machine):** `mvn verify` now runs
`AbstractIntegrationTest`/`SchemaIntegrityTest` for real via Testcontainers - Flyway applies all 11
migrations against a live Postgres 16 container and every `SchemaIntegrityTest` assertion (37
tables, both extensions, all 7 functions, all 16 named indexes, `crm_app` grants, audit-chain
verification, audit immutability, CHECK constraints) passes. This is now a fully build-verified
phase, not just SQL-verified by hand.

## Phase 3 - Security foundation  [COMPLETE, build-verified 2026-08-28]
**`./mvnw -q verify` green: 25/25 tests pass (9 SchemaIntegrityTest + 9 SecurityGrantMatrixTest + 6
AuthenticationIntegrationTest + 1 CrmBackendApplicationTests). `spec-audit` run before closing this
phase - see notes below for the one gap it found and the fix.**
- [x] Entities: `Permission, Role, RolePermission, Department, Position, User` (package `user`,
  mapped exactly against `V2__security_and_org.sql`; `RolePermission` modeled as its own entity per
  FINAL_DOMAIN_MODEL.md 1.3, not a plain join table)
- [x] `UserDetails` adapter (`AppUserDetails`/`AppUserDetailsService`) + `AuthenticationManager`
  bean, used only for the login-time credential/status check via `DaoAuthenticationProvider` - this
  was missing from the first pass and added after `spec-audit` flagged it against
  FINAL_IMPLEMENTATION_ORDER.md's literal Phase 3 line. `PermissionAuthorityResolver` resolves
  permissions fresh per request from `role_permission`, never from the token or from
  `UserDetails.getAuthorities()` (which deliberately returns empty)
- [x] `JwtService` (HS256, access 15 min / refresh 7 day, no permissions in the token),
  `JwtAuthenticationFilter` (re-loads the user by id every request for the live status check),
  real `SecurityFilterChain` (`shared.config.SecurityConfig`, replaces the Phase 1 placeholder
  wholesale, `@EnableMethodSecurity` for `@PreAuthorize`)
- [x] BCrypt encoder (strength 10), `CustomAuthenticationEntryPoint` + `CustomAccessDeniedHandler`
  (`shared.security`), standard error body (`shared.exception.ErrorResponse` /
  `ErrorResponseWriter`) - note `@PreAuthorize` denials surface as `AuthorizationDeniedException`
  *inside* the controller invocation and never reach the filter-level handler, so
  `GlobalExceptionHandler` also has an explicit `AccessDeniedException` mapping to the same 403 body
- [x] `POST /api/auth/login`, `POST /api/auth/refresh`, `POST /api/auth/logout`, `GET /api/auth/me`
  (only login/refresh are public - fixed a bug during this phase where `/api/auth/**` was wrongly
  public, which let unauthenticated requests reach `/auth/me`)
- [x] `@RestControllerAdvice` (`GlobalExceptionHandler`) + `DomainException` hierarchy
  (`ValidationException/AccessDeniedDomainException/NotFoundException/ConflictException/
  IllegalStateTransitionException`) from API_SPEC.md §9. `DataIntegrityViolationException` has only
  a generic 409 fallback for now - per-constraint-name translation is explicitly Phase 12's job
  per FINAL_IMPLEMENTATION_ORDER.md, not silently skipped
- [x] Seeded grant matrix asserted in `SecurityGrantMatrixTest` against SECURITY_SPEC.md §3 -
  verified row-by-row by hand during `spec-audit` too, exact match across all 13 sections x 7 roles
- [x] S-01 (`AuthenticationIntegrationTest`: no token -> 401 UNAUTHENTICATED; expired token -> 401
  TOKEN_EXPIRED) and S-02 (a token issued while ACTIVE is rejected with 403 once the user is
  flipped to BLOCKED, proving the filter re-checks the DB live, not the token) both pass
- [x] "Login returns a working token" and "protected endpoint 401 without a token / 403 with an
  insufficient permission" DoD lines covered; the 403 case needed a test-only `@PreAuthorize`
  endpoint since no real business endpoint exists until Phase 4's admin CRUD

**Environment fix alongside this phase:** the Testcontainers Postgres container was being torn down
and recreated (on a new port) between test classes once a third/fourth class started extending
`AbstractIntegrationTest`, causing intermittent `Connection refused` failures. Fixed by switching
`AbstractIntegrationTest` to Testcontainers' documented "singleton container" pattern (manual
`static { POSTGRES.start(); }`, no `@Testcontainers`/`@Container` annotations) instead of
annotation-driven per-class lifecycle management.

New `[DEMO]` assumptions recorded: `ASSUMPTIONS.md` A12 (JWT secret env-var/fallback handling) and
A13 (no server-side refresh-token tracking, so `/auth/refresh` and `/auth/logout` don't rotate or
invalidate anything - already flagged out of scope in SECURITY_SPEC.md §10).

## Phase 4 - Reference data + admin CRUD  [COMPLETE, build-verified 2026-08-28]
**`./mvnw -q verify` green: 39/39 tests pass. `spec-audit` run before closing this phase.**

A concurrent session had already written part of this phase (and some Phase 8 code) directly into
the repo before this session started on it - see the note at the bottom of this section for what
was found, reused, and fixed.

- [x] Entities: `Service, WorkType, ExternalStage` (found already written, reused as-is after
  verification against `V3__reference_data.sql`)
- [x] Admin endpoints (`admin` package, all new this session): users (list/create/update/block),
  departments (list/create/update), positions (list/create/update), role permissions
  (get/patch grant set for a role), services, work types, external stages (list/create/update each)
  - `USER_ADMIN:*` gates users/departments/positions/role-permissions;
    `REFERENCE_DATA:*` gates services/work-types/external-stages
    (mapping decision recorded in ASSUMPTIONS.md A16)
  - No object-level (layer-2) policy on these endpoints - global reference/admin config has no
    ownership dimension for a `CaseAccessPolicy`-style check to apply to, unlike case/task/document
    data in later phases
  - Admin-created users cannot be assigned the `APPLICANT` role (400) - that role only ever
    attaches through Phase 5's self-registration flow, the only path that also sets `applicantId`
- [x] `AuditWriter` port + JPA implementation (found already written, reused after fixing a real
  bug - see below) - wired into every one of the 7 admin services' mutations, confirmed by grep
  (`create`/`update`/`block` each call `auditWriter.write(...)`)

**A-04 passes**: `AdminRolePermissionAuditTest` patches `/admin/roles/OPERATOR/permissions` through
the real endpoint, then queries `audit_log` directly and asserts the newest
`ROLE_PERMISSION_CHANGED` row has `case_id IS NULL` and non-null `old_value`/`new_value`.
**A-02/A-03** already pass via Phase 2's `SchemaIntegrityTest` (still green, not re-tested).
**S-12 explicitly deferred to Phase 8**: it needs `GET /admin/price-rules`, and `PriceRule` (while
it already exists as an entity, written ahead of schedule - see below) has no admin-exposed
resource yet; Phase 4's own scope line names only `Service, WorkType, ExternalStage`, not
`PriceRule`, so building that endpoint now would be reaching into Phase 8 territory rather than
finishing Phase 4's own DoD.

**Concurrent-session code found and reused (2026-08-28):** before starting this phase, files with
timestamps after this session's Phase 3 work were found already in the repo: `audit/*`
(`AuditWriter`, `AuditEvent`, `AuditActor`, `AuditActorResolver`, `AuditLog`, `AuditAction`,
`AuditLogRepository`, `JpaAuditWriter`), `application/Service.java` + `ServiceRepository` +
`SubmissionChannel`, `work/WorkType.java` + repository, `workflow/ExternalStage.java` + repository,
`shared/dto/PageResponse.java`, and (Phase 8 scope, ahead of schedule) `finance/PriceRule.java` +
`PriceRuleRepository` + `PriceRuleType` + `shared/domain/ProcessingMode.java`. Every file was read
and verified against the relevant Flyway migration and domain-model doc before reuse. Genuinely
high quality, but incomplete: no admin endpoints existed yet, no tests, and `JpaAuditWriter`
imported `com.fasterxml.jackson.databind.ObjectMapper` - the Jackson 2 type Spring Boot 4.1 no
longer autoconfigures a bean for (same bug already fixed in `ErrorResponseWriter` during Phase 3;
Boot 4.1 defaults to Jackson 3 / `tools.jackson`). Fixed by swapping the import, same as before.
`AuditActorResolver`'s javadoc also referenced a nonexistent "ASSUMPTIONS.md A16"; fixed to point at
A14, the entry this session actually wrote for that decision. `PriceRule`/`PriceRuleType`/
`ProcessingMode` are left in place, unwired and unused until Phase 8 - noted here so a future
session doesn't duplicate them.

**AuditAction extended (ASSUMPTIONS.md A15):** added `USER_UPDATED`, `DEPARTMENT_CHANGED`,
`POSITION_CHANGED` to the enum and to `V10__audit.sql`'s `ck_audit_action`/`ck_audit_case_scope`
CHECK constraints - FINAL_DOMAIN_MODEL.md 9 calls its own list a "minimum set," and Phase 4's admin
edit operations needed actions that weren't in it. Safe to edit locally: Testcontainers gives every
`mvn verify` run a fresh ephemeral Postgres, no persisted Flyway checksum to fight.

## Phase 5 - Applicant & application  [COMPLETE, build-verified 2026-08-28]
**`./mvnw -q verify` green: 53/53 tests pass. `spec-audit` run before closing this phase.**

- [x] `Applicant` with `@GroupSequenceProvider`-driven validation groups (H6) -
  `applicant.validation.ApplicantGroupSequenceProvider` picks `IndividualGroup`/`LegalEntityGroup`
  off the request's own `type`; every type-specific field carries both a required constraint for
  its own type and a `@Null` constraint for the other, so "wrong-type field present" is a
  validation failure too, not just "right-type field missing" (API_SPEC.md 2)
- [x] `Application` with `submissionChannel` incl. `PAPER` (H7) - `formData` JSONB carries
  route-configured fields plus, in this phase, the submitted item composition under an `items`
  key (no `application_item` table exists; `CaseItem` rows only get materialized at Phase 7's
  `register`, reading this same JSON)
- [x] Endpoints from API_SPEC.md §2/§3 except `/register` (Phase 7 - needs `ElectronicCase`)

**Two genuine spec gaps found and resolved this phase** (both recorded in `ASSUMPTIONS.md`, not
silently guessed): **A17** - `POST /applicants`'s documented authz ("APPLICANT (self)") presupposes
an already-authenticated `APPLICANT` principal, circular for a first-time signup, and no
`/auth/register` endpoint exists anywhere; resolved by making `POST /applicants` public and having
it atomically create `Applicant` + `User{role=APPLICANT, applicantId}` - the self-registration flow
Phase 4's admin endpoint already pointed to without it existing yet. **A18** - API_SPEC.md says
staff with `APPLICATION:CREATE` can intake PAPER/SINGLE_WINDOW applications, but the seeded grant
matrix (tested exactly by `SecurityGrantMatrixTest`) gives that permission to `APPLICANT` only;
left the seed untouched and documented that a deployment grants it to a staff role via Phase 4's
own admin endpoint if it wants that intake path, rather than diverging code from the tested table.

**I-03/I-04/I-05 pass**, each asserting both the REST-layer rejection (Bean Validation groups /
service-level check) and the database CHECK constraint directly, per the DoD's explicit wording:
- I-03 (`ApplicantValidationTest`): INDIVIDUAL missing `pinfl` → `400` and `ck_applicant_individual`
- I-04 (`ApplicantValidationTest`): LEGAL_ENTITY missing `tin` → `400`; an INDIVIDUAL-only field
  present on a LEGAL_ENTITY payload → `400`; direct insert → `ck_applicant_legal`
- I-05 (`ApplicationCrudTest`): PAPER without a resolvable registrar → `422`; the same payload from
  a staff caller with `APPLICATION:CREATE` (granted via Phase 4's admin endpoint in the test) →
  `201`; direct insert without `registered_by_id` → `ck_application_paper_registrar`

Two-layer authorization (SECURITY_SPEC.md 5-6) confirmed for both `Applicant` and `Application`:
permission check (`APPLICATION:VIEW`, which `APPLICANT` already holds) at the controller, explicit
ownership check in the service, and a `404` (not `403`) when an applicant reaches for someone
else's row - asserted directly (`anotherApplicantCannotSeeThisOne`).

**Deferred, not cut:** `UpdateApplicantRequest`'s "before first registration" lock (API_SPEC.md 2)
can't be enforced yet - nothing before Phase 7's `register` endpoint tracks whether any of an
applicant's applications reached `REGISTERED`. Owner edits are unconditionally allowed for now.
Staff application listing is unscoped (`ASSUMPTIONS.md` A20) until Phase 7 links applications to
departments via `ElectronicCase`.

## Phase 6 - Workflow configuration & versioning  [COMPLETE, build-verified 2026-08-28]
**`./mvnw -q verify` green: 67/67 tests pass. `spec-audit` run before closing this phase.**

- [x] `Workflow, WorkflowStage, WorkflowTransition` (`workflow` package, mapped exactly against
  `V4__workflow.sql`). `Workflow` deliberately has no `@Version` field - V4 has no optimistic-lock
  column on it; immutability of a published row is a service-layer contract (copy-on-write), not a
  database one, matching V4's own comment ("a DB trigger is intentionally NOT used here: DRAFT rows
  must stay editable")
- [x] `WorkflowDefinitionService`: `createDraftVersion` (deep copy with stage-id remapping for
  transitions), `updateStages` (full replace, DRAFT only - **W-12** passes: `PATCH` on a published
  version → `409 WORKFLOW_VERSION_IMMUTABLE`), `publish` (runs `WorkflowPublishValidator`'s seven
  rules, then retires the previous `ACTIVE` row *before* activating the draft in the same
  transaction - required ordering, see the bug note below), `retire`
- [x] `WorkflowPublishValidator` - all seven WORKFLOW_ENGINE_DESIGN.md §2 rules, each unit-tested in
  isolation (`WorkflowPublishValidatorTest`, no Spring/DB): entry transition exists, every stage
  reachable, no cycle, every parallel group has a required member, every `approvalRequired` stage
  has a mode, exactly one `COMPLETION` stage, `expeditedDeadlineDays <= deadlineDays`
- [x] **C-07** passes (`WorkflowConcurrencyTest`): two simultaneous `publish` calls on different
  draft versions of the same code, `ExecutorService` + `CountDownLatch` per TEST_MATRIX.md §F's
  prescribed pattern - exactly one wins, `uq_workflow_one_active` is what actually arbitrates it
  (no custom locking code needed)
- [x] Seed workflow v1 `ACTIVE` + v2 `DRAFT` in `src/main/resources/db/demo/V900__demo_seed_data.sql`
  (new - `db/demo` didn't exist before this phase), wired into `local`/`demo`/`test` Flyway
  locations only. Trimmed from the full reference seed in `docs-2/FLYWAY_MIGRATIONS/demo/` to just
  what workflow versioning needs (`ASSUMPTIONS.md` A21); v2 has no transitions yet, deferred to
  whichever phase needs to actually publish it (A22, W-11)

**Two real bugs found and fixed by the test suite itself, not just eyeballed:**
1. `updateStages`'s delete-then-recreate of a draft's stage/transition graph could trip
   `uq_workflow_stage_sequence`/`uq_transition_single_entry` because the deletes hadn't flushed to
   the database before the inserts ran in the same persistence-context flush. Fixed with an explicit
   `entityManager.flush()` between the deletes and the inserts.
2. `updateStages` initially threw `IllegalStateTransitionException` (422) for
   `WORKFLOW_VERSION_IMMUTABLE`, but API_SPEC.md 9 lists that code under **409** (immutability), not
   422. Fixed to `ConflictException`. `WorkflowLifecycleTest`'s W-12 test is what caught this.

**Test-ordering note:** `WorkflowLifecycleTest` uses `@TestMethodOrder`/`@Order` because several of
its tests assert against whatever is currently the `ACTIVE` version of the shared seeded
`WASTE_CERT_ROUTE`, and one of its own tests (`publishActivatesDraftAndRetiresPreviousActiveWithAudit`)
changes that - it's ordered to run last. `createDraftVersionCopiesSeededV1` additionally queries the
live stage/transition count instead of a hardcoded number, since other test *classes* in the same
shared-container run also touch this code family.

## Phase 7 - Case creation and the engine core  [COMPLETE, build-verified 2026-08-29]
**`./mvnw -q clean verify` green: 88/88 tests pass. `spec-audit` run before closing this phase.**

A prior session in this repo (same day, ran out of budget mid-phase) had already written the full
production code: `ElectronicCase, CaseStage, CaseItem, PrimaryCheck, CaseComment`;
`WorkflowEngine` (`activateStage`/`completeStage`/`advance`), `TransitionEvaluator` + one
`ConditionHandler` per `ConditionType`, `StageActivator` per `StageType`, `DeadlineCalculator`,
`ExternalStageMapper`; `POST /applications/{id}/register`, `GET /cases`, `/cases/{id}`,
`/cases/{id}/timeline`, `/cases/{id}/tracking`, `POST /cases/{id}/primary-check`,
`GET`/`PUT /cases/{id}/items`, `POST`/`GET /cases/{id}/comments`. It had also written the full
`AbstractCaseIntegrationTest` fixture (department/service/applicant/workflow/stage/transition
seeding helpers) and two dependency-free unit test classes (`PrimaryCheckEvaluatorTest`,
`ExternalStageMapperTest`, covering U-01…U-04 and U-14) - but had NOT yet written a single
integration test exercising the fixture, so I-01, I-02, I-06…I-09, W-01, W-03, S-07 and the
"registration is one transaction" DoD line were all unverified despite the code looking complete.
This session's work was closing that gap, not re-implementing Phase 7.

- [x] Read the existing code in full before writing anything (`CaseService` 842 lines,
  `WorkflowEngine` 318 lines, `CaseAccessPolicy`, all engine/activator/condition classes) and
  confirmed it against `WORKFLOW_ENGINE_DESIGN.md` and `FINAL_DOMAIN_MODEL.md` 4 - genuinely solid:
  correct idempotency via `uq_case_stage`, correct `current_stage_id = NULL` handling for parallel
  groups, correct two-layer authorization, correct pessimistic-lock parallel gate
  (`lockRequiredSiblings`, `SELECT ... FOR UPDATE`).
- [x] Fixed one real bug found while writing tests: `AbstractCaseIntegrationTest#seedWorkflow`
  built an ACTIVE workflow without `published_at`, tripping `ck_workflow_published` (V4) on the
  very first insert - every test using the fixture would have failed at setup. Fixed by setting
  `publishedAt = Instant.now()` in the helper.
- [x] `CaseLifecycleIntegrationTest` (new, 10 tests) covers the phase's full DoD line:
  I-01/I-02 (one case, one `case_stage` row per workflow stage, workflow pinned to the ACTIVE
  version, entry stage activated, audit trio `CASE_CREATED`/`CASE_REGISTERED`/`STAGE_ACTIVATED` all
  scoped to the case), W-01 (sequential route driven stage-by-stage to `COMPLETED` via the engine,
  standing in for Phase 8/9's not-yet-built completion endpoints exactly as their own classes'
  javadoc says those phases should), W-03 (`current_stage_id` NULL with two ACTIVE parallel
  siblings, applicant tracking still resolves to exactly one external stage, and the
  `ALL_REQUIRED_PARALLEL_TASKS_DONE` gate correctly blocks on one incomplete required sibling),
  I-06 (category/decision persist independently), I-09 (decision without a reason -> 400), I-07
  (RETURNED_TO_APPLICANT -> status RETURNED, tracking shows reason+remarks), I-08 (ROUTE_CHANGED
  rebinds workflow, audits `ROUTE_CHANGED`, preserves the abandoned route's reached stage as
  CANCELLED history rather than silently deleting it), S-07 (tracking JSON string-searched for
  internal field names/labels/department ids - none present), and S-05 (an applicant reading
  another applicant's case -> 404, not 403) - not in this phase's literal DoD line but it is
  `CaseAccessPolicy`'s own behavior and cheap to pin down now that the fixture exists.
- [x] Found and fixed a second, unrelated bug while getting a full `mvn verify` green: Phase 6's
  `WorkflowConcurrencyTest` (C-07) was flaky under a full 87+ class suite - not because
  `WorkflowDefinitionService.publish()` is wrong (confirmed correct in isolation, and
  `uq_workflow_one_active` is what actually arbitrates it), but because a warmed-up JVM/connection
  pool can let two "concurrent" `ExecutorService` threads complete their whole HTTP-to-SQL round
  trip without ever truly overlapping at the UPDATE statement, so both publishes legitimately
  succeed sequentially (retire-then-activate, twice) instead of colliding. Fixed by retrying the
  race up to 20 times with fresh draft pairs, asserting the true invariant
  (`activeCount == 1`, race or no race) on every attempt and requiring a genuine collision
  (`successCount == 1`) on at least one. Not a Phase 7 deliverable, but a red build - fixed rather
  than carried forward, per this file's non-negotiable rule.
- [x] Two Phase 7 documentation debts closed: `docs-2/ASSUMPTIONS.md` A24-A34 were cited by number
  in Phase 7 code's own javadoc (`CaseLifecycleResolver`, `StageActivatorRegistry`,
  `ApprovalRoundCompletedConditionHandler`, `PaymentStateProvider`, `CaseAccessPolicy`,
  `PrimaryCheckEvaluator`, `CaseResponse`/`ApplicantTrackingResponse`/`ApplicantTrackingMapper`,
  `WorkflowRepository`, `AbstractCaseIntegrationTest`, `CaseService` x4) but had never actually been
  written to the file - the prior session ran out of budget before appending them. Reconstructed
  and appended all eleven from their citing code's own reasoning (A23 left as a reserved, unused
  number rather than renumbering everything after it).
- [x] A29 records a real, if narrow, gap surfaced by writing the ROUTE_CHANGED test itself:
  `findByServiceIdAndStatusOrderByCodeAsc` picks the first ACTIVE workflow for a service by code
  order when more than one exists, which is deterministic but arbitrary - a real deployment needs
  an explicit service-to-route mapping. Demo/test data should keep one ACTIVE workflow per service
  until that exists.

**Deferred, not cut (all already flagged in the code's own javadoc, not discovered here):** the
`exists task(...)` clause of `canViewCase` (A26, Phase 9), `ApprovalStateProvider`/
`PaymentStateProvider` have no implementation (A25, Phases 8/10), `CaseResponse.finance` and
`ApplicantTrackingResponse`'s contract/payment/document/notification fields stay null (A28, Phases
8/10/12), case items are locked by `CaseStatus` rather than a real `Contract` row (A33, Phase 8),
internal comments are unaudited pending a `ck_audit_action` migration (A32).

## Phase 8 - Accounting, pricing, contract, payment  [COMPLETE, build-verified 2026-08-29]
**`./mvnw -q clean verify` green: 110/110 tests pass (88 carried over from Phase 7 + 22 new).
`spec-audit` run before closing this phase.**

- [x] `PriceCalculation, PriceCalculationLine, Contract, Payment, PaymentConfirmation` (`finance`
  package, mapped exactly against `V6__finance.sql`; `PriceRule`/`PriceRuleType` already existed
  from Phase 4). `PaymentConfirmation` is `@Immutable` (matches `AuditLog`'s precedent) - the DB's
  `tr_payment_confirmation_immutable` trigger is the real guarantee, Hibernate's is the second layer.
- [x] `PriceCalculator` - dependency-free (`new PriceCalculator()`, same style as
  `PrimaryCheckEvaluator`/`DeadlineCalculator`/`ExternalStageMapper`): the caller resolves applicable
  `PriceRule` rows from the DB, this class only does the arithmetic of ASSUMPTIONS.md A3
  (`sum(item.basePrice * item.quantity) * modeCoefficient + additionalWorkFees`, floored at
  `MINIMUM_TOTAL`). `PriceCalculatorTest` (7 tests) covers U-05 … U-08.
- [x] `AccountingService` (`setProcessingMode`, `calculatePrice`/`getPrice`, `confirmPrice`,
  `recordContract`, `getPayment`/`confirmPayment`/`setPaymentStatus`) - same shape as
  `CaseService`: one transaction per public method, `AuditWriter` on every mutation (all 10
  accounting-section `AuditAction` values are now emitted by a real code path, ahead of Phase 11's
  "every value used" requirement for this section), `CaseAccessPolicy` for the object-level layer.
  `AccountingController` wires API_SPEC.md 5's 8 endpoints with `FINANCE:*` `@PreAuthorize` per its
  table.
- [x] `PaymentWaitingScheduler` (`@Scheduled`, `@EnableScheduling` added to
  `CrmBackendApplication`) flags overdue payments and audits `PAYMENT_OVERDUE` - confirmed by test
  it **never** changes `PaymentStatus` or the case's route (spec 12.9, H5). The "creates a manager
  decision task" half of the DoD line is a port (`finance.port.OverduePaymentTaskCreator`, consumed
  via `ObjectProvider`, no-op when absent) for Phase 9 to implement - same seam pattern as Phase 7's
  A25, recorded as A35.
- [x] Calculated vs actual price: `confirmPrice` never overwrites `Contract.calculatedAmount`;
  overriding to a different `actualAmount` requires `reason` (400 otherwise) and sets
  `amountChangedById`/`amountChangedAt` in the same write - `ck_contract_change_tracked` is the DB's
  own copy of the same rule. I-12 asserts both the reject-without-reason and the preserved-original
  paths through the real endpoint.
- [x] Flow wiring verified end to end: `recordContract` (all 4 fields required together per
  `ck_contract_sent`/I-13) completes the ACCOUNTING `case_stage` via `WorkflowEngine`, which
  activates PAYMENT_CONTROL (`PaymentControlStageActivator`, Phase 7, stamps `paymentDueAt`) -
  `AccountingService` then creates the `Payment` row in the same transaction. `confirmPayment`
  recomputes `debtAmount`/`status` exactly per the DB CHECKs, and only completes PAYMENT_CONTROL
  when the payment gate is actually open (full payment, or partial on a route with
  `allowExecutionBeforeFullPayment` - I-14/I-15, plus a third test for the early-execution branch
  neither named row explicitly covers).
- [x] `S-03`/`S-04`: `403 APPLICANT_CANNOT_SET_MODE` is an explicit check inside
  `AccountingService.setProcessingMode` (defense-in-depth alongside `@PreAuthorize`, mirroring
  `CaseAccessPolicy.requireStaff`'s existing "two layers, always" pattern) - tested by temporarily
  granting `FINANCE:EDIT` to `APPLICANT` so the request actually reaches the service, same technique
  Phase 7 used for `APPLICATION:EDIT`/`CASE:EDIT`. `422 EXPEDITED_NOT_ALLOWED` gates on
  `workflow.isExpeditedAllowed()`.
- [x] Tightened `CaseService`'s item-lock (A33) to also fire once a `CONFIRMED` `PriceCalculation`
  exists, via a new port (`casemodule.port.PriceConfirmationChecker`, implemented in `finance`) -
  `casemodule` still has no compile-time dependency on `finance`, matching the direction every other
  cross-phase seam in this codebase already runs.
- [x] Two new `[DEMO]` assumptions recorded: `ASSUMPTIONS.md` A35 (overdue-task-creation port) and
  A36 (a mode change AFTER price confirmation does not auto-recalculate; requires an explicit new
  `price/calculate` call - silently recalculating a number that may already be on a signed contract
  was judged riskier).

**Not directly tested (implemented, not a DoD gap):** `GET /accounting/cases/{id}/price` and
`GET /accounting/cases/{id}/payment` are only exercised indirectly today (via repository checks
after POST calls in the integration tests), not through their own HTTP assertion.

## Phase 9 - Execution block  [COMPLETE, build-verified 2026-08-29]
**`./mvnw -q clean verify` green: 119/119 tests pass (114 carried over from Phase 8 + 5 new).
`spec-audit` run before closing this phase.**

First drafted by a different agent (Gemini 3.1 Pro), which reported false completion - see the
"second lesson" note above the Phase 1 section for the full account of what that meant and how each
issue was found and fixed. Summary of what's actually true now:

- [x] `Task, TaskResult` (`task` package, mapped exactly against `V7__execution.sql`),
  `TaskFactory` (dependency-free construction from a `StageActivationContext`, reuses Phase 7's
  `DeadlineCalculator` rather than reimplementing deadline math), `TaskService` (one transaction per
  public method, `AuditWriter` on every mutation), `ExecutionStageActivator` +
  `FinalReviewStageActivator` (one `Task` per activated EXECUTION/FINAL_REVIEW stage; PRIMARY_CHECK
  deliberately gets none - ASSUMPTIONS.md A37). `TaskController` wires API_SPEC.md 6's 11 endpoints.
- [x] Assignment (department-head-of-that-task-only, S-09), reassignment, start, submit result
  (version-chain supersession per spec 7.13, `entityManager.flush()` between superseding the live
  result and inserting the new one - `uq_task_result_live` trap, same shape as Phase 6/7's), approve
  result (idempotent re-approval; the optional comment goes into the audit row since `task_result`
  has no comment column), return to revision (reuses the same task row per `uq_task_case_stage`,
  never inserts a second one), complete (delegates entirely to
  `WorkflowEngine.completeStage` - no gating logic reimplemented in this phase).
- [x] The parallel gate (`CaseStageRepository.lockRequiredSiblings`,
  `SELECT ... FOR UPDATE`) is Phase 7's, reused unchanged, exactly as the DoD expects. A completed
  task replay (`/complete` called twice) returns the same body without advancing the workflow a
  second time - `TaskService` short-circuits on `TaskStatus.COMPLETED` before touching anything, and
  `WorkflowEngine.completeStage` is independently idempotent underneath that as a second safety net.
- [x] `finance/port/OverduePaymentTaskCreator` (Phase 8's port) implemented by
  `OverduePaymentTaskCreatorImpl` - idempotent per PAYMENT_CONTROL `case_stage` (A38).
  `CaseAccessPolicy`'s task-existence clauses (A26's original gap) are now real for both
  DEPARTMENT_HEAD and SPECIALIST - see A39 for why SPECIALIST keeps a department-touch fallback
  alongside them rather than matching SECURITY_SPEC.md 5's bare pseudocode exactly.
- [x] `TaskLifecycleIntegrationTest` (new, 5 tests, added when closing out this phase) drives every
  task through the REAL activation pipeline (register → primary-check ACCEPTED → `WorkflowEngine` →
  `ExecutionStageActivator`/`FinalReviewStageActivator` → `TaskFactory`, never a hand-inserted row)
  and covers W-02, W-05, W-06, W-07, W-09, W-10. `TaskExecutionIntegrationTest` (Gemini's original,
  kept after the `grant()` fix made it pass legitimately) covers S-08, S-09, W-04, and a version of
  W-08. `AllRequiredParallelTasksDoneConditionHandlerTest` (dependency-free) covers U-10, closing a
  gap left open since Phase 7.

**Not tested (implemented, not a DoD gap):** `GET /tasks`, `GET /tasks/my`, `GET /tasks/{id}` are
exercised indirectly (via repository/status checks after mutating calls) but have no dedicated
assertion on their own filtering/pagination behavior.

## Phase 10 - Documents, approvals, signing  [COMPLETE, build-verified 2026-08-29]
**`./mvnw -q clean verify` green: 129/129 tests pass (119 carried over from Phase 9 + 10 new).
`spec-audit`-equivalent review done before closing this phase.**

First drafted by Gemini 3.1 Pro under an explicit "production code only, no tests, no verify"
instruction (to conserve that session's own budget) - this session's job was entirely verification
and fixing, same discipline as Phase 9's review, just starting from code that had never been run
even once. What was found and fixed:

- [x] **Build-breaking bug**: `DocumentVersion.contentHash` mapped as a plain `String` for a
  `char(64)` DB column - `ddl-auto=validate` failed at Spring context startup, cascading to 85 of
  119 existing tests erroring before any Phase 10 code even ran. Same well-documented trap as
  `PriceRule`/`Contract`'s `currency` columns elsewhere in this codebase; fixed with
  `@JdbcTypeCode(SqlTypes.CHAR)`.
- [x] **Real authorization bug**: `ApprovalService#requireTaskAssignee`'s `if/else` chain had no
  branch for `ParticipantKind.ACCOUNTING` and fell through without throwing - meaning any
  authenticated user could decide an ACCOUNTING-kind approval task. Rewritten as an exhaustive
  `switch` over all four kinds, each branch throwing `NOT_TASK_ASSIGNEE` on a mismatch.
  ACCOUNTING now requires the `ACCOUNTANT` role.
- [x] **Real authorization gap**: `DocumentController`'s three GET endpoints used
  `@PreAuthorize("isAuthenticated()")` instead of `hasAuthority('DOCUMENT:VIEW')` - since
  `CaseAccessPolicy.grantsView` returns `true` unconditionally for `OPERATOR` (org-wide monitoring),
  this let OPERATOR read documents despite holding no `DOCUMENT:*` permission in the seeded matrix.
  Fixed to the standard two-layer pattern every other phase uses (`ApprovalController`'s
  `isAuthenticated()` on `/approval-rounds/{id}` and `/approvals/my` is correct as-is - no
  `APPROVAL:VIEW` permission exists in the seed at all, confirmed against `V2__security_and_org.sql`).
- [x] Error codes standardised to match the rest of the codebase and API_SPEC.md's own naming:
  `"VALIDATION_ERROR"` (used nowhere else in this codebase) replaced with `"VALIDATION_FAILED"`
  (`DocumentService`'s two checks) and the API_SPEC-named `"APPROVAL_COMMENT_REQUIRED"`
  (`ApprovalService.reject`, D-04) instead of a generic code.
- [x] **D-01 gap**: creating a new `DocumentVersion` never marked the previous one `SUPERSEDED` -
  only `supersedes_id` was set. Fixed in `DocumentService.createVersion`.
- [x] **Completeness gap**: when a required participant rejects, the round closes immediately
  (correct, spec 13.4) but every other still-open `ApprovalTask` was left dangling in
  SENT/IN_REVIEW forever - `ApprovalTaskStatus.SKIPPED` exists specifically for this and was never
  used. Fixed in `ApprovalService.reject` (with the DB's `ck_approval_task_decided` CHECK - caught
  by the new test - requiring `decided_by_id`/`decided_at` on the skipped rows too, attributed to
  the rejection that closed the round).
- [x] `SigningService` (S-10: only `HEAD_OF_CERTIFICATION_BODY`, checked explicitly in the service
  as defense-in-depth alongside `@PreAuthorize`, same "two layers always" pattern as Phase 7/8/9;
  D-08: `422 APPROVAL_NOT_COMPLETED` before the round finishes, DB trigger independently rejects a
  signed version's status changing) and `ApprovalService`'s round-completion path (D-05, D-06) both
  correctly delegate case advancement to `WorkflowEngine.completeStage` - no gating logic
  reimplemented, matching Phase 9's established pattern.
- [x] `LocalDocumentStorageAdapter` ([DEMO]) is an honest, minimal stub: API_SPEC.md 7 never
  receives raw file bytes (client supplies `contentRef`/`contentHash` directly, no multipart upload
  endpoint exists), so it only checks the ref is non-blank - documented as the deliberate MVP
  reading, not a placeholder someone forgot to finish.
- [x] `DocumentApprovalIntegrationTest` (new, 10 tests) covers D-01 … D-08, S-10 end to end through
  the real HTTP endpoints, including two direct-SQL DB-trigger checks (D-02, and the signed-version
  immutability half of D-08) matching the style already established in Phase 7/9's tests.
- [x] Light audit-detail enrichment on the highest-value events (`DOCUMENT_SIGNED`,
  `APPROVAL_ROUND_STARTED`, `APPROVAL_REJECTED`) which previously recorded no detail at all despite
  the request carrying a note/comment/mode right there - not an exhaustive pass, the remaining
  `auditWriter.write` calls in this phase still pass `null` for old/new value maps.

**Deferred, not cut (recorded in ASSUMPTIONS.md, not discovered by anyone else):** SEQUENTIAL
approval-round ordering is stored (`sequenceNo`, `mode`) but not enforced - any participant may
decide at any time regardless of mode (A40). `Document`/`DocumentVersion`/`ApprovalRound`/
`ApprovalTask` use JPA `@ManyToOne` associations instead of this codebase's established raw-UUID-FK
convention - works today because every access stays inside its owning transaction, but is an
architectural inconsistency and a latent `LazyInitializationException` risk (A41).

## Phase 11 - Performed works + full audit coverage  [COMPLETE, build-verified 2026-08-30]
**`./mvnw -q clean verify` green: 143/143 tests pass (129 carried over from Phase 10 + 14 new).
`spec-audit`-equivalent review done before closing this phase.**

First drafted by Opus 4.6 (in Antigravity) under the same "production code only, no tests, no
verify" split that worked for Phase 10 - this session's job was again entirely verification and
fixing. What was found and fixed, roughly in order of severity:

- [x] **Build-breaking bug, same class as Phase 10's**: `PerformedWork.createdAt` mapped to a
  `created_at` column that does not exist in `V9__performed_work.sql` (only `recorded_at` does -
  `FINAL_DOMAIN_MODEL.md`'s prose lists a `createdAt` field the actual migration never got; per
  CLAUDE.md the migration is authoritative). Failed Spring context startup, cascading to 89-95 of
  129 existing tests erroring before any Phase 11 code ran. Fixed by deleting the phantom field -
  `recordedAt` already serves this purpose per the real schema.
- [x] **Real, reproducible data bug**: `PerformedWorkRecorder` passed `electronicCase
  .getProcessingMode()` straight into a `NOT NULL` column, but that field stays `null` until an
  accountant explicitly sets it - a stage can complete (and this recorder can fire) before that
  ever happens. Caught as an actual `23502` failure while writing PW-01's test, not spotted by
  inspection alone. Fixed to default to `TRADITIONAL`, matching `DeadlineCalculator`'s existing
  convention for the same "unset means TRADITIONAL" case (ASSUMPTIONS.md A43).
- [x] **Real privacy/security bug, same class as Phase 10's `DocumentController` issue**:
  `GET /cases/{id}/performed-works` was gated on `isAuthenticated()` instead of
  `PERFORMED_WORK:VIEW` - since `CaseAccessPolicy.requireCanView` alone lets an applicant into
  their own case, this let the applicant see performed-work data, directly contradicting spec
  15.13 (the exact thing Phase 7's S-07 test exists to prevent on the tracking endpoint). Fixed
  and added a dedicated test proving the applicant now gets 403.
- [x] **Real, previously-unreachable feature**: nothing in Phase 10's `DocumentService` ever set
  `document.task_id`, so `PerformedWorkRecorder`'s "supporting document" lookup (spec 8.3) could
  never find anything through the real API. Added an optional `taskId` to `CreateDocumentRequest`
  and wired `DocumentService.createDocument` to set it (ASSUMPTIONS.md A42) - without this, PW-01
  could only be tested by bypassing the API entirely.
- [x] Cosmetic/dead-code cleanup: `@PreAuthorize("hasAuthority('PERFORMED_WORK:VIEW') or
  hasAuthority('ADMIN')")` - `hasAuthority(...)` checks a permission code, never a role name, in
  this codebase's security model (`PermissionAuthorityResolver` never puts role names into
  authorities), so `hasAuthority('ADMIN')` could never match anything. Removed the dead clause.
  Also fixed `workTypeCode` resolution in `PerformedWorkService.search` silently falling back to
  "no filter" when the code didn't match any real `WorkType` instead of correctly returning zero
  rows.
- [x] `PerformedWork, PerformedWorkRecorder` (upsert on `(caseId, workTypeId, caseStageId)`, spec
  8.5's revision-cycle contract: a second completion of the same `case_stage` updates only
  `supportingDocumentVersionId`, never inserts a second row), `ContractAmountBracketResolver`
  (dependency-free, no Spring/DB, same style as `DeadlineCalculator`/`PriceCalculator`) - U-13's
  boundary behaviour at exactly 10/20/30M verified directly (ASSUMPTIONS.md A4's lower-inclusive/
  upper-exclusive rule).
- [x] `GET /performed-works`, `GET /cases/{id}/performed-works`, and the three audit endpoints
  (`GET /audit`, `GET /cases/{id}/audit`, `GET /audit/integrity`) - none of these three existed in
  any form before this phase.
- [x] Every `AuditAction` value confirmed emitted by at least one real code path via a grep sweep
  run BOTH before this phase (to find the gaps) and after (to confirm they closed) - `TASK_CREATED`
  (`TaskFactory`, Phase 9 gap), `APPROVAL_SENT` (`ApprovalService.startRound`, Phase 10 gap),
  `ROUTE_ASSIGNED` (`CaseService.register`, Phase 7 gap - `CASE_CREATED`'s payload already carried
  the same information, but this makes the enum value unambiguously real) were all genuine gaps in
  EARLIER phases' code, closed here rather than papered over. `PRICE_RULE_CHANGED`,
  `REPORTING_ACCESS_CHANGED`, `CONFIDENTIAL_DATA_ACCESSED` remain honestly declared
  out-of-scope-in-this-slice (no `/admin/price-rules` endpoint exists, and spec section 18
  reporting/analytics is out of scope for this entire project per CLAUDE.md) - confirmed correct
  by an independent re-run of the same sweep, not just trusted from the handoff report.
- [x] `PerformedWorkAndAuditIntegrationTest` (new, 6 tests) covers PW-01, PW-02 (via a direct
  second call to `PerformedWorkRecorder.recordIfConfigured` - `WorkflowEngine.completeStage` is
  itself idempotent per `case_stage`, so this is the correct way to exercise the recorder's own
  upsert logic without inventing a stage-reopening mechanism nothing else in the codebase has),
  PW-03 (both directions of the DB trigger, via direct SQL), A-01 (a full happy-path walkthrough
  asserting every expected `AuditAction` is present, plus basic seq ordering), and A-05 (intact →
  forced tamper via a temporarily-disabled trigger, in a `try`/`finally` - ASSUMPTIONS.md A44 -
  → `GET /audit/integrity` reports the exact broken `seq`). `ContractAmountBracketResolverTest`
  (new, 8 tests, dependency-free) covers U-13.

**Deferred, not cut:** the SEQUENTIAL approval-round ordering gap from Phase 10 (A40) and the
`@ManyToOne`-vs-raw-UUID inconsistency in Document/Approval entities (A41) are unchanged by this
phase - neither was in this phase's scope, and both are already honestly recorded.

## Phase 12 - Concurrency, idempotency, hardening  [COMPLETE (Swagger deferred), build-verified 2026-08-30]
- [x] `command_log` via `IdempotencyFilter` - a request-level filter (not an interceptor: needs the
  raw body before the controller consumes it), wired after `JwtAuthenticationFilter` in
  `SecurityConfig`. Engages only when `Idempotency-Key` is present on POST/PUT/PATCH. Custom
  `CachedBodyHttpServletRequest` lets both the filter and the controller read the body (Spring's
  own `ContentCachingRequestWrapper` cannot be read twice by two different readers). Replay with
  the same key+body returns the stored response; same key + different body -> `409
  IDEMPOTENCY_KEY_REUSED`. See ASSUMPTIONS A45. Tests: `IdempotencyFilterIntegrationTest` (C-05).
- [x] `@Version` verified on every mutable aggregate - grepped every real `@Version` field (12
  hits, distinct from javadoc mentions) against every `version bigint` DB column (12 hits) across
  all 11 pre-existing migrations: exact 1:1 match (`app_user, applicant, application,
  electronic_case, primary_check, case_stage, contract, payment, task, document, approval_round,
  approval_task`). Nothing missing, nothing extra. No code change needed - already correct from
  earlier phases.
- [x] `DataIntegrityViolationException` -> business error code translation by constraint name -
  `ConstraintViolationTranslator`, a plain map from every reachable `uq_*` constraint/partial index
  to a specific `(status, code, message)`, wired into `GlobalExceptionHandler`. `ck_*` CHECK
  constraints deliberately left unmapped (see ASSUMPTIONS A46). Unit-tested directly
  (`ConstraintViolationTranslatorTest`), no Spring context needed.
- [x] C-01…C-06 concurrency tests written (`ConcurrencyIntegrationTest`, C-07 already existed from
  Phase 6 as `WorkflowConcurrencyTest`) - genuine `ExecutorService` + `CountDownLatch` races, not
  sequential replays. Writing them for real (not trusting code review) surfaced three real bugs,
  all fixed:
  - Two genuine PostgreSQL deadlocks under simultaneous parallel-stage completion (see ASSUMPTIONS
    A47): `CaseStageRepository.lockRequiredSiblings`'s old single `SELECT ... FOR UPDATE ORDER BY
    id` does not actually guarantee lock-acquisition order in PostgreSQL; and
    `WorkflowEngine.completeStage()` locked its own row before the sibling group, letting two
    siblings deadlock reaching for each other's row. Both fixed at the source. As defense in depth,
    a residual `PessimisticLockingFailureException` (deadlock or lock-wait-timeout) is now mapped
    to the same `409 CONCURRENT_MODIFICATION` as a lost `@Version` race, everywhere in the app.
  - A genuine race in `audit_log_chain()`'s hash computation (V10), only ever exposed by two truly
    concurrent audit writes (nothing before this phase had any) - see ASSUMPTIONS A48. Fixed in
    `V12__audit_chain_serialize.sql` with a transaction-scoped advisory lock, verified against a
    standalone two-connection reproduction outside the Spring app before trusting the fix.
  - C-04 (stale PATCH -> 409) needed no new code: `AdminUserService.update` already compared
    `request.version()` against the loaded entity and threw `ObjectOptimisticLockingFailureException`
    - just needed the actual test (`AdminUserCrudTest.aStalePatchWithAnOutdatedVersionIsRejected`).
- [ ] Swagger finalised: all endpoints, auth, DTOs, error responses. **Deliberately deferred, not
  forgotten** - zero `@Operation`/`@Tag` annotations exist anywhere in the 18 controllers yet;
  springdoc still auto-generates a working `/swagger-ui.html` from the Spring MVC mappings alone
  (title/description/per-endpoint summaries and the price/workflow demo-assumption notice are
  what's missing), it just isn't polished. Do this as the FIRST task of a future session before
  Phase 13, budget permitting - it is pure, low-risk, mechanical annotation work with no
  architecture decisions left to make.

Full suite: `./mvnw -q clean verify` green, 156/156 (143 carried over + 13 new: 4
`ConcurrencyIntegrationTest`, 5 `ConstraintViolationTranslatorTest`, 3
`IdempotencyFilterIntegrationTest`, 1 `AdminUserCrudTest` addition).

## Phase 13 - Demo data, README, self-review  [COMPLETE, build-verified 2026-08-30]
- [x] `demo/V900` produces a fully walkable scenario - extended with demo users (9, one bcrypt
  hash shared by all: ASSUMPTIONS A45's convention), one applicant, and price rules for
  `WASTE_CERTIFICATION`, on top of Phase 6's workflow-only skeleton. Verified not by clicking
  through Swagger by hand but by `DemoScenarioWalkthroughTest`
  (`src/test/java/uz/ithunter/crm/integration/`) - a real, automated, end-to-end walk of
  FINAL_IMPLEMENTATION_ORDER.md's own demo script, through the real `/api/auth/login` endpoint and
  every real business endpoint, against the real seed data, all the way to a COMPLETED case.
- [x] README with all 14 required sections (problem, scope, architecture, domain model, workflow,
  security, how to run, test users, demo scenario, API, testing, assumptions, out of scope, future
  extensions), demo-assumption disclosures throughout, at the project root.
- [x] `docs-2/FINAL_REVIEW.md` - an independent Senior Reviewer pass. Found and fixed ONE CRITICAL:
  `ApprovalStateProvider` (the `ENDORSEMENT -> SIGNING` engine gate) was never implemented since
  Phase 7 shipped it as an interface-only seam - no test had ever exercised the real
  `APPROVAL_ROUND_COMPLETED` condition end-to-end, only `DemoScenarioWalkthroughTest` did, and it
  caught this on the first real run. Fixed with `uz.ithunter.crm.approval.CaseApprovalStateProvider`
  (see ASSUMPTIONS A49). No HIGH findings remained open (three from earlier phases were already
  fixed and are documented in those phases' own sections, not repeated in FINAL_REVIEW.md). Two
  MEDIUM and one LOW finding recorded and left open by design - see the document itself.
- Full suite: `./mvnw -q clean verify` green, **157/157** (156 carried over + 1 new
  `DemoScenarioWalkthroughTest`).

## Out of scope for this slice (declared, not forgotten)
- [-] Field work 7.5.x: GPS, photo metadata, graphic signature, offline Late Upload, file hashing
- [-] Template-based document generation (section 6) and template admin UI
- [-] Certificates, registry, QR, annulment (14.7, section 19)
- [-] Didox / Single Window / customs API integrations (outbound port interface only)
- [-] Notifications (15.12)
- [-] Reporting and analytics, PDF export, workload charts (section 18)
- [-] Frontend
