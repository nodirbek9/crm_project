# CRM Backend — IT Hunter technical assignment

Backend-only vertical slice of a certification-body CRM (application → case → primary check →
routing → accounting/payment → parallel execution → documents/approval → signing → audit).
Frontend is out of scope. This is an interview deliverable: correctness, defensible architecture
and honest scope statements matter more than feature count.

**The design work is already done.** An architect pass (Opus, thinking) produced the full spec,
review, domain model, schema, workflow engine design, security spec, API spec, test matrix and a
13-phase execution plan in `docs-2/`. Your job in this repo is disciplined implementation against
that plan — not re-deriving it. Re-reading and second-guessing the architecture wastes budget that
should go into code and tests.

## Read this first, every session

1. `docs-2/IMPLEMENTATION_STATUS.md` — what phase you're on, right now. This is the source of
   truth for progress, not memory of a previous conversation.
2. `docs-2/FINAL_IMPLEMENTATION_ORDER.md` — the phase you're on: exact scope + Definition of Done.
3. Only the docs that phase needs (see the map below). Don't load all of `docs-2/` into context
   for a Phase 3 task — it's ~120KB total and most of it is irrelevant to auth/JWT work.

Do not start writing code before doing this. Do not re-run gap analysis against the original spec —
`docs-2/PLAN_REVIEW.md` already did that; its 4 CRITICAL + 7 HIGH fixes are baked into every other
doc in `docs-2/`.

## Doc map — read only what the phase needs

| File | Use it for |
|---|---|
| `docs-2/MASTER_PROMPT_V2.md` | The full brief — architecture, domain list, all 13 [FIX n] corrections, transaction boundaries, demo data, execution rules. Read once at the start, re-read the relevant `[FIX n]` when touching that area. |
| `docs-2/FINAL_DOMAIN_MODEL.md` | Aggregates, fields, enums, cardinality, ownership — before writing any entity. |
| `docs-2/DATABASE_SCHEMA.md` + `docs-2/FLYWAY_MIGRATIONS/V1..V11` | Exact schema. Entities must match it (`ddl-auto=validate`) — never the other way round. |
| `docs-2/WORKFLOW_ENGINE_DESIGN.md` | Before touching `workflow`/`case`/engine code — versioning, gating, idempotency, concurrency. |
| `docs-2/API_SPEC.md` | Endpoint list, DTOs, status/error codes, authz per endpoint. |
| `docs-2/SECURITY_SPEC.md` | Roles, the 7×13 permission matrix, object-level policy (`CaseAccessPolicy`), admin limits. |
| `docs-2/TEST_MATRIX.md` | The 58 named tests, mapped to spec clauses. A test with no clause doesn't belong. |
| `docs-2/ASSUMPTIONS.md` | Every DEMO value (A1–A10). Any new ambiguity you resolve gets appended here, marked `[DEMO]` — never presented as a real client rule. |
| `docs-2/ARCHITECTURE_REVIEW.md` | The 5 interview questions this design invites, and the honest answers. Read before final review / README. |
| `docs-2/PLAN_REVIEW.md` | Why each `[FIX n]` exists. Only if you need the "why", not the "what". |

## Project layout

- `crm-backend/` — the Maven project. Java 21, Spring Boot 4.1, package root **`uz.ithunter.crm`**
  (already renamed from the Initializr default `hexlet.code.crmbackend` — keep using
  `uz.ithunter.crm.<module>` for every new class: `auth, user, applicant, application, casemodule,
  workflow, document, task, approval, finance, work, audit, admin, shared`). Java's `case` keyword
  means the case module package is `casemodule`, not `case`.
- `docs-2/` — architecture. Read-only reference; update `IMPLEMENTATION_STATUS.md` and
  `ASSUMPTIONS.md` as you go, don't touch the rest.
- `pom.xml` already has flyway, springdoc, mapstruct, jjwt, testcontainers, validation, assertj
  wired in (added ahead of Phase 1 — MASTER_PROMPT_V2 lists these as required but the Initializr
  skeleton only had web/jpa/security/postgres/lombok).

## Non-negotiable global rules (every phase)

- `mvn -q verify` must be green before you consider a phase done. A red build is never carried
  forward to the next phase.
- `spring.jpa.hibernate.ddl-auto=validate`. Flyway owns the schema. If an entity doesn't match, fix
  the entity, not the migration (unless the migration itself is wrong against `DATABASE_SCHEMA.md`).
- No JPA entity is ever returned from a controller. No business logic in a controller or repository
  — it lives in application services.
- Any spec ambiguity you hit: check the original spec intent → pick the MVP-reasonable assumption →
  append it to `docs-2/ASSUMPTIONS.md` marked `[DEMO]` → continue. Don't stop and ask "should I
  continue" — that's covered by MASTER_PROMPT_V2's execution rules. Do stop and ask when a decision
  is genuinely irreversible or contradicts an explicit `[FIX n]`.
- Don't rewrite a previous phase's working code without a failing test justifying it.
- Update `docs-2/IMPLEMENTATION_STATUS.md` at the end of every phase — check off what's done, note
  what's deferred and why.
- Two authorization layers, always: does the principal hold the permission at all (endpoint), and
  may this principal touch *this* object (object-level, via `CaseAccessPolicy` or equivalent).
  Layer 1 alone is the classic interview trap (`SPECIALIST` with `TASK:EDIT` editing every task).

## Working rhythm in Claude Code

- One phase = one branch = one (or a few) commits. Cheap to revert if a phase goes sideways.
- Use plan mode before a phase that touches more than 2-3 files: let it show the plan against the
  DoD before writing code.
- `/clear` between phases. Stale context from Phase 4 is the most common reason Sonnet starts
  "fixing" Phase 6 code nobody asked it to touch.
- Prefer Sonnet 5 at **high** effort for phase implementation (new services, workflow engine,
  security). Drop to **medium** for repetitive mechanical work within a phase (DTOs, mappers,
  Flyway-to-entity field copying, boilerplate tests). Escalate a specific stuck problem to Opus
  rather than re-prompting Sonnet repeatedly on the same failure.
- After a phase's DoD passes, run the `spec-audit` skill before marking it done in
  `IMPLEMENTATION_STATUS.md` — it checks the phase's code against the matching `TEST_MATRIX.md`
  rows and `SECURITY_SPEC.md` grant matrix instead of trusting a green build alone.
- Use the `run-phase` skill to start the next phase — it enforces the read-order above before any
  code gets written.

## What NOT to do

- Don't implement anything listed as "out of scope for this slice" in `IMPLEMENTATION_STATUS.md`
  (field work / GPS / graphic signature, template generation, certificates/registry/QR, external
  integrations beyond a port interface, notifications, reporting/analytics, frontend) unless
  explicitly asked.
- Don't invent permissions beyond the seven in spec 16.4 (VIEW, CREATE, EDIT, ENDORSE, APPROVE,
  SIGN, BLOCK).
- Don't merge `HEAD_OF_CERTIFICATION_BODY` and `DEPARTMENT_HEAD` — that's the single most-checked
  design decision in this project (see `ARCHITECTURE_REVIEW.md` Q4-adjacent reasoning and
  `PLAN_REVIEW.md` C2).
- Don't claim a requirement is implemented when it isn't — `IMPLEMENTATION_STATUS.md` must stay
  honest; false checkmarks defeat the entire point of this file.
