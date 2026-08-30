# FLYWAY_MIGRATIONS

SQL text only — nothing here needs to be executed to be reviewed.
PostgreSQL 16 dialect. Place under `src/main/resources/db/migration`.

Order:

| File | Contents |
|---|---|
| `V1__extensions_and_conventions.sql` | extensions, shared trigger functions |
| `V2__security_and_org.sql` | permission, role, role_permission, department, position, app_user, user_role |
| `V3__reference_data.sql` | service, work_type, external_stage |
| `V4__workflow.sql` | workflow, workflow_stage, workflow_transition |
| `V5__applicant_application_case.sql` | applicant, application, electronic_case, primary_check, case_item, case_stage, case_comment |
| `V6__finance.sql` | price_rule, price_calculation, price_calculation_line, contract, payment, payment_confirmation |
| `V7__execution.sql` | task, task_result |
| `V8__document_and_approval.sql` | document, document_version, approval_round, approval_task |
| `V9__performed_work.sql` | performed_work |
| `V10__audit.sql` | audit_log + immutability trigger + hash chain + grants |
| `V11__idempotency.sql` | command_log for request-level idempotency |
| `demo/V900__demo_seed_data.sql` | **[DEMO]** seed — separate location, enabled only by the `demo` profile |

`spring.flyway.locations=classpath:db/migration` for prod,
`classpath:db/migration,classpath:db/demo` for the `demo` and `local` profiles.

Conventions used throughout:
* `uuid` primary keys, `gen_random_uuid()` default (pgcrypto).
* Enums are stored as `varchar` + `CHECK (... IN (...))`, not native PG enums — adding a value is a cheap
  migration and JPA `@Enumerated(STRING)` maps cleanly. Documented trade-off in `DATABASE_SCHEMA.md`.
* Money: `numeric(18,2)`; currency `char(3)` default `UZS`. No floating point anywhere.
* Timestamps: `timestamptz`, defaulted to `now()` where they mean "row created".
* `version bigint NOT NULL DEFAULT 0` on mutable aggregates only (JPA `@Version`).
* Every table with mutable state gets `updated_at` maintained by the `set_updated_at()` trigger.

## Two review notes on the SQL

1. **Grant ordering.** `V10` grants `crm_app` and then sets `ALTER DEFAULT PRIVILEGES`, so `command_log`
   created in `V11` is covered automatically. If you add migrations later that create tables, either rely
   on the default privileges or re-run the grant block. Flyway itself must run as the schema **owner**,
   never as `crm_app` — otherwise it cannot create the audit triggers it is revoking rights against.
2. **Untyped `NULL` in `VALUES` lists** (demo seed). Each such column has at least one non-null literal
   in the same list, so PostgreSQL infers the type. If you reorder those rows, add an explicit cast
   (`NULL::varchar`) rather than debugging a type-inference error.
