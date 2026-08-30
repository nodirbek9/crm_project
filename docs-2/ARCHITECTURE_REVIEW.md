# ARCHITECTURE_REVIEW.md
Independent review of the design above, before any code exists. Findings are about the *design*, not
about missing implementation.

## Strengths (what this design actually buys)

1. **The route is data.** Spec 1.6 / 5.12 / 16.14 demand configuration without code changes, and the
   engine honours it: adding a condition or stage type is one class in a registry, not a new branch.
2. **`case_stage` as a runtime instance table.** It is the keystone. It gives idempotent activation
   (unique key), honest parallel gating (indexed sibling read), a real per-case timeline, and it keeps
   the immutable config immutable. Neither input document had it.
3. **Invariants live in the database.** `ck_contract_change_tracked`, `ck_approval_task_reject_comment`,
   `uq_performed_work_once`, the audit trigger. Spec 20.3 and 13.5 speak of protection; a service method
   is not protection.
4. **Two-layer authorization with an explicit policy object.** `CaseAccessPolicy` is called from
   services, not woven by an aspect, so it is reviewable and testable per role.
5. **Every demo value is labelled.** Tariffs, criteria, deadlines and boundary rules are marked
   `[DEMO]` in the seed SQL, in the DTOs (`demoNotice`) and in Swagger. Nothing pretends to be a client
   rule.

## Residual risks, honestly stated

| Sev | Finding | Mitigation in this design |
|---|---|---|
| MEDIUM | The `case_stage` model materialises every stage at registration. A route change mid-flight (`ROUTE_CHANGED`) must reconcile stage rows against a different workflow. | Handled as: cancel unstarted stage rows (`CANCELLED`), materialise the new route's stages, keep completed rows for history. Documented, and it is the ugliest corner of the design. Say so before being asked. |
| MEDIUM | The hash chain serialises audit writes on `SELECT ... ORDER BY seq DESC LIMIT 1`. Under real concurrency this is a hot row. | Acceptable at this scale, and named as such. Production answer: periodic Merkle checkpoints instead of a per-row chain, or ship to an append-only store. |
| MEDIUM | `jsonb` payloads (`form_data`, `task_result.payload`, electronic-blank fields) are unvalidated blobs in this slice. Spec 5.2 / 7.6 make their shape route-configured, which really implies a JSON-schema-per-stage feature. | Out of scope, stated. The column is ready; the schema registry is not. |
| LOW | `403` vs `404` asymmetry between applicants and staff. | Deliberate, documented in `SECURITY_SPEC.md` §6. |
| LOW | Deadlines are calendar-day based. Spec 5.8 says nothing about business hours or holidays. | `[DEMO A5]`. A real implementation needs a working-calendar service — the spec's own section 20 sibling requirement (work schedules) is absent. |
| LOW | One `Payment` per case. Spec 12.6 allows multi-item contracts but says nothing about split invoicing. | Single contract, single payment, itemised lines. Stated as an assumption. |

## Five questions the interviewer will almost certainly ask

**1. "Your workflow config is immutable and versioned. What happens to a case that is mid-route when
someone publishes a new version, and what happens if a case has to *switch* routes?"**
The strong half of the answer is easy: nothing happens, because `electronic_case.workflow_id` pins the
version, v1's rows are never mutated, and the case never re-resolves its route by code (test W-11).
The half they are really fishing for is `ROUTE_CHANGED` (spec 4.7): completed `case_stage` rows are kept
for history, unstarted ones are `CANCELLED`, the new route's stages are materialised, and the switch is
audited as a user decision. Do not pretend this is elegant. Name the alternative you rejected
(snapshotting the whole route as JSON onto the case) and why: queryability.

**2. "Two specialists finish the last two parallel tasks at the same millisecond. Walk me through
exactly what prevents the next stage from being created twice."**
Three things, and you should name all three because any one alone is insufficient:
`SELECT ... FOR UPDATE` on the required siblings of the parallel group serialises the two gate
evaluations; `uq_case_stage (case_id, workflow_stage_id)` makes a duplicate insert impossible even if
they did race; and `activateStage` no-ops when the stage is already `ACTIVE`, so the loser returns
success instead of an error. Then explain why optimistic locking on the case alone is *not* enough here:
the gate reads sibling rows and writes a different row, so a version bump does not order the readers.
Test C-02 asserts `activation_count = 1`.

**3. "Your audit log is 'append-only'. I'm a DBA with superuser. Prove it."**
Correct answer: *I can't prevent you, and the design says so.* What it does: no write API, the
application role holds only `INSERT`/`SELECT`, a trigger blocks `UPDATE`/`DELETE`/`TRUNCATE` even if the
grant is wrong, and every row hashes the previous one so any rewrite breaks the chain and
`verify_audit_chain()` reports the exact `seq`. So tampering moves from *possible and invisible* to
*possible and provable*. Spec 20.3 asks for protection from all user categories including the
Administrator; the honest reading is that full immutability needs storage outside the DB (WORM, log
shipping), which is named as the production step. Claiming more than this is the actual failure mode.

**4. "Why is `Document`/`DocumentVersion` separate from `TaskResult`? Isn't that duplicate versioning
machinery?"**
Because they version different things and the spec approves different things. `TaskResult` is the
*outcome of a task*, approved by a department head (spec 4.15). `DocumentVersion` is a *document*, and
endorsement in spec 13.3–13.5 targets a specific version, with remarks bound to that version and
multiple participants deciding independently. Collapsing them means an endorsement remark cannot point
at "version 2 of the technical specification", which breaks 13.5 outright. The shared shape
(`version_no`, `supersedes_id`, `revision_reason`, an immutability trigger) is deliberate symmetry, and
it is factored into a small `Versioned` interface plus one reusable trigger function rather than copied.

**5. "Show me where money and permissions can go wrong. Who can change a contract amount, and can an
admin see it?"**
Only `ACCOUNTANT` holds `FINANCE:APPROVE`. Overriding the calculated price cannot be stored without
`changed_by`, `changed_at` and a reason (`ck_contract_change_tracked`), and the original calculation is
a separate immutable `price_calculation` row, never an `UPDATE` (spec 12.4). `ADMIN` holds no
`FINANCE:*` and `canViewCase` returns false for admins (spec 16.17), and where an admin legitimately
reads commercial configuration, an interceptor writes `CONFIDENTIAL_DATA_ACCESSED` because spec 16.18
requires it unconditionally. Expect the follow-up: *"so who audits the accountant?"* — the audit log,
plus the fact that CRM never moves money (spec 1.14): accounting only records a confirmation, so the
worst case is a wrong *record*, reconciled against Didox and the invoice reference stored in
`contract.invoice_reference`.

## One thing to volunteer before they find it

The whole field-work block (spec 7.5.x: GPS, photo provenance, graphic signature, offline Late Upload,
file integrity) is out of scope, and it is the most technically interesting part of the original
specification. Say that first, unprompted, and say what the design would be: an offline-first client
with a client-generated idempotency key per blank, `performed_at` separate from `recorded_at` (already
modelled in `performed_work`), and `content_hash` on every uploaded artefact (already modelled in
`document_version`). Volunteering the gap with a sketch of the answer reads as judgement. Being caught
without it reads as an oversight.
