# External Go/No-Go Review — `nodirbek9/crm_project` @ `main` (tree `1cc3f75`)

Reviewed 2026-08-30. Read-only pass. Nothing modified.

## Verdict

**Presentable with one mandatory fix, and it is a small one.** The engineering I was able to read is
genuinely above take-home level: `CaseStageRepository.lockRequiredSiblings` does row-by-row
`SELECT ... FOR UPDATE` in ascending `id` order and explains, correctly and in its own words, why the
single-statement `ORDER BY ... FOR UPDATE` form deadlocks in PostgreSQL — that comment alone will carry
a concurrency conversation. `CaseAccessPolicy` implements a real two-layer model, gets the 404-vs-403
distinction right and for the right stated reason, and returns `false` for `ADMIN` with spec 16.17
cited. `docs-2/FINAL_REVIEW.md` is a credible self-review that found a real class of bug
(`ApprovalStateProvider`) rather than a cosmetic one, and it names its own limits. **But**: `pom.xml`
hardcodes the author's macOS Docker socket into the surefire configuration, so `./mvnw clean verify`
— the first command the assignor will run, and the command the README tells them to run — fails on
any machine that is not the author's laptop. Every integration test depends on Testcontainers via
`AbstractIntegrationTest`, so this is not a partial failure, it is a red suite on a repo whose own
docs claim 157/157 green. Fix that one line and this is a strong submission. Ship it as-is and the
first impression is "the build doesn't work."

Second-order, not blocking but worth knowing before the conversation: the audit-immutability story in
`docs-2/` is one layer thinner than documented (the `crm_app` grant layer is provisioned and then
never used by any profile), and that same migration creates a passwordless login role with full DML on
every table. Neither breaks the demo; both are the kind of thing a careful reviewer notices in the
first five minutes and asks about.

## CRITICAL

### 1. `pom.xml` — hardcoded `DOCKER_HOST` breaks `./mvnw clean verify` for everyone but the author

`pom.xml`, `maven-surefire-plugin` configuration block:

```xml
<environmentVariables>
  <DOCKER_HOST>unix:///Users/user/.docker/run/docker.sock</DOCKER_HOST>
</environmentVariables>
```

This injects an absolute macOS-specific socket path into the test JVM's environment, **overriding**
whatever working Docker configuration the reviewer's machine has. `src/test/java/uz/ithunter/crm/AbstractIntegrationTest.java`
starts its `PostgreSQLContainer` in a static initializer, and every integration test in the suite
extends it — so on a Linux box, on a Mac with any other username, or under a Docker setup that does
not use that exact path, container startup fails and the entire integration suite errors out at class
init. The unit tests will pass and everything else will not.

Why it matters beyond the inconvenience: `docs-2/FINAL_REVIEW.md` closes with "157/157 tests green
after C1's fix". If the assignor's first command produces a wall of Testcontainers connection errors,
that claim reads as untrue rather than as environment-specific, and it undermines every other
verified statement in the docs.

Shape of the fix: delete the `<environmentVariables>` block outright — Testcontainers discovers the
daemon on its own, which is why it works everywhere else. If a local override is genuinely needed,
`~/.testcontainers.properties` or a developer-only Maven profile is the place for it, never the
committed default build.

## HIGH

### 2. `src/main/resources/db/migration/V10__audit.sql` — the `crm_app` role is created, granted, and never used; and it has no password

Two distinct problems in the same block (lines beginning `DO $$ ... CREATE ROLE crm_app LOGIN;`):

**a) The documented "layer 2" of audit protection is inert.** The migration's own comment says "the
application connects as `crm_app`, which is granted INSERT+SELECT on `audit_log` only", and
`docs-2/DATABASE_SCHEMA.md` ("Audit strategy", layer 2) and `docs-2/SECURITY_SPEC.md` §9 both build on
that. Nothing connects as `crm_app`: `application-demo.yaml` uses `username: crm` (the database owner
created by `docker-compose.yml`'s `POSTGRES_USER: crm`), and `AbstractIntegrationTest` uses
`crm_test`, also an owner. Both bypass every `REVOKE` in the file. The audit log *is* still immutable
in practice — `tr_audit_log_immutable`, `tr_audit_log_no_truncate` and the hash chain are real and do
the work — so this is not a data-integrity hole. It is a claim the code does not back. An interviewer
who reads `SECURITY_SPEC.md` and then asks "show me the application connecting as the restricted
role" gets no answer, and the honest one ("it doesn't, the trigger is what actually holds") is much
better delivered by you first than discovered by them.

**b) A production migration provisions an unpassworded login account with full DML.**
`CREATE ROLE crm_app LOGIN` with no password, followed by
`GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO crm_app` and a matching
`ALTER DEFAULT PRIVILEGES`. Under any `pg_hba.conf` that permits `trust` or password-less auth for
that role, this is an open door on every table in the schema — including `contract`, `payment` and
`applicant`, which is precisely the commercial and personal data spec 20.1 and 16.18 are about. This
migration is in `db/migration`, not `db/demo`, so it runs under the `prod` profile too. Either the
role should be created outside Flyway by whoever owns the environment, or the migration should set a
password from an environment variable and the application should actually use it.

### 3. `CaseAccessPolicy` reaches into another module's repository, contradicting the stated module boundary

`src/main/java/uz/ithunter/crm/casemodule/CaseAccessPolicy.java` — field
`private final ObjectProvider<TaskRepository> taskRepository;`, used in `grantsView` for both the
`DEPARTMENT_HEAD` and `SPECIALIST` branches.

`docs-2/FINAL_DOMAIN_MODEL.md` §0 states the rule plainly: "Cross-module access is allowed **only**
through each module's public application service and DTOs; repositories are package-private to their
module." Here the `casemodule` package injects `uz.ithunter.crm.task.TaskRepository` directly, and the
`ObjectProvider` wrapper is a lazy-lookup workaround — the tell for a bean cycle between the two
modules rather than a deliberate design choice. The concrete risk is small; the defensibility cost is
not, because it sits in the class the security story is built on, and "why is this one dependency
lazy?" is an obvious question. Either introduce a small port in `casemodule` that the `task` module
implements (the pattern the codebase already uses elsewhere — `docs-2/FINAL_REVIEW.md` describes
`.../port/*.java` interfaces with exactly this shape), or drop the claim from the docs.

## MEDIUM

### 4. `CaseService.java` is 46 KB, against the docs' own "no god classes"

`src/main/java/uz/ithunter/crm/casemodule/CaseService.java` — 46,159 bytes, the largest file in the
repository by a wide margin (`CaseMapper.java` is next at 17,097). `docs-2/WORKFLOW_ENGINE_DESIGN.md`
§1 specifies the engine as separate collaborators (`WorkflowEngine`, `TransitionEvaluator`,
`ConditionHandler` per condition type, `StageActivator` per stage type, `TaskFactory`,
`DeadlineCalculator`), and the code-quality section the project was built against asks for small
services and no god classes.

**Explicitly not verified:** I did not open this file. Some of that decomposition demonstrably exists
— `docs-2/FINAL_REVIEW.md` names `ApprovalRoundCompletedConditionHandler` and a `port` package — so
this may be a large-but-organised orchestrator rather than a monolith. Treat it as "check whether the
documented component split is real, and if it is, note that the docs' file-size ambition slipped";
do not treat it as a confirmed structural defect.

### 5. Stated stack is Spring Boot 3.x; the build is Spring Boot 4.1.1

`pom.xml` uses `spring-boot-starter-parent` `4.1.1` and the Boot 4 starter names
(`spring-boot-starter-webmvc`, `spring-boot-starter-flyway`, `spring-boot-starter-data-jpa-test`,
`spring-boot-resttestclient`). `docs-2/MASTER_PROMPT_V2.md` and the surrounding docs specify
"Spring Boot 3.x". Harmless and arguably an upgrade, but it is a visible mismatch between the
specification the repo says it implements and the repo, and it is free to fix in one line of prose.

### 6. The demo cannot be walked without an undocumented-by-the-API permission grant first

`docs-2/FINAL_REVIEW.md` M1, confirmed against `V2__security_and_org.sql`: registering an application
requires `APPLICATION:EDIT`, and the seeded grant matrix gives that permission to no role, so a fresh
`docker compose up` reaches step 2 of the demo scenario and stops until an admin calls
`PATCH /api/admin/roles/DEPARTMENT_HEAD/permissions`. The project knows this and documents it as an
intentional consequence of the spec's own permission table (`ASSUMPTIONS.md` A30), with a README
"step 0" and coverage in `DemoScenarioWalkthroughTest`.

That is an honest handling of it, and the reasoning is correct. It is still the second thing the
assignor will hit, and "the seeded system cannot perform its own first business operation" lands badly
even when documented. Seeding the grant in `db/demo/V900` — demo data, not the spec-derived matrix in
`V2` — would remove the wart without weakening the argument that the spec's table is incomplete.
Keep the `ASSUMPTIONS.md` A30 note either way; it is a good answer to "did you notice?".

## LOW

### 7. `SPECIALIST` case visibility is wider than `SECURITY_SPEC.md` §5 specifies

`CaseAccessPolicy.grantsView`, `SPECIALIST` branch: access is granted on
`departmentTouchesCase(...) OR task assigned to me OR task assigned to my department`. The spec's
pseudocode is task-existence only. Net effect: any specialist in a case's main-responsible or
participating department can read that case with no task assigned to them.

The deviation is deliberate, documented (`ASSUMPTIONS.md` A39) and justified in a long comment on the
method — a task genuinely does not exist yet at the `PRIMARY_CHECK` step, so the literal pseudocode
breaks a working flow. That is the right call and the right paper trail. Flagged only so it is not a
surprise if the reviewer diffs the spec against the code, which this repo invites them to do.

## Checked and found fine (one line each, no action)

- `docker-compose.yml` + `application-demo.yaml` are coherent: the backend resolves `postgres:5432`
  over the compose network, and the demo profile adds `classpath:db/demo` to the Flyway locations.
  `FINAL_REVIEW.md`'s L1 (compose unverified) looks like a genuine port clash in their sandbox, not a
  latent config error — I found nothing that would stop it on a clean machine, though I could not run
  it either (see below).
- `application.yaml` sets `ddl-auto: validate`, so entity/migration drift fails the boot rather than
  silently reshaping the schema. This is the single most valuable line in the config.
- `V10__audit.sql` matches `docs-2/FLYWAY_MIGRATIONS/V10__audit.sql` and correctly implements the
  nullable `case_id` + `ck_audit_case_scope` design, the `BEFORE INSERT` hash chain, the
  UPDATE/DELETE/TRUNCATE guards and `verify_audit_chain()`. The enum gained
  `USER_UPDATED`/`DEPARTMENT_CHANGED`/`POSITION_CHANGED` with an `ASSUMPTIONS.md` A15 reference — spec
  drift handled the right way.
- `AbstractIntegrationTest` uses the singleton-container pattern with a written rationale for not
  using `@Testcontainers`/`@Container`; real Postgres 16, no H2 anywhere, which the constraint- and
  trigger-level assertions require.
- Test tree exists and is substantial: 14 module packages plus a dedicated `integration` package. The
  suite is real, not a stub.
- `CaseStageRepository` implements the parallel gate exactly as `WORKFLOW_ENGINE_DESIGN.md` §6
  describes, excludes `required = false` siblings in SQL rather than in Java, and documents the
  lock-ordering deadlock it fixes. Best single artifact in the repository.

## What I did not verify — stated so nothing above is mistaken for more than it is

- **I could not run anything.** No build, no test run, no `docker compose up`. Finding 1 is derived by
  reading `pom.xml` against `AbstractIntegrationTest`, not by observing a failure. The 157/157 claim
  is therefore unconfirmed in either direction; on the author's own machine it is probably accurate.
- **Files read in full:** `pom.xml`, `docker-compose.yml`, `application.yaml`, `application-demo.yaml`,
  `src/main/resources/db/migration/V10__audit.sql`, `casemodule/CaseAccessPolicy.java`,
  `casemodule/CaseStageRepository.java`, `AbstractIntegrationTest.java`, `docs-2/FINAL_REVIEW.md`.
- **Files not opened:** `CaseService.java`, `ApprovalService.java`, `CaseMapper.java`,
  `WorkflowDefinitionService.java`, `WorkflowPublishValidator.java`, the condition handlers and
  `port` interfaces, the `task`/`finance`/`document`/`work`/`audit`/`auth` implementations, `README.md`,
  migrations other than V10, and every individual test. In particular I did **not** independently
  re-hunt for a second `ApprovalStateProvider`-shaped gap across the transition handlers — that was the
  prompt's central ask and it remains the largest un-executed part of this review. The reliable way to
  close it is not more reading: it is to assert, in a test, that every `ConditionType` enum value used
  by the seeded demo route resolves to a registered handler bean, and that every handler bean is
  reachable from at least one seeded transition. That test would have caught C1 in Phase 7 and will
  catch its siblings now.
- `SecurityGrantMatrixTest`, `DemoScenarioWalkthroughTest` and `CaseLifecycleIntegrationTest` are
  referenced by `FINAL_REVIEW.md` and by comments in the code I read; I confirmed the test packages
  exist but did not open those classes, so I am relying on the same evidence the self-review is.
