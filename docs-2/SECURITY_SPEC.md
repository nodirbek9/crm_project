# SECURITY_SPEC.md

Two independent layers, both required:
1. **Endpoint authorization** — does this principal hold the permission at all?
2. **Object-level authorization** — may this principal touch *this* case / task / document?

Layer 1 alone is the classic interview trap: `SPECIALIST` holding `TASK:EDIT` could otherwise edit
*every* task in the system. Spec 3.4, 7.5.6 and 5.5 all demand layer 2 explicitly.

## 1. Authentication

* JWT, HS256 **[DEMO]** secret from env (`JWT_SECRET`); production note in README: rotate to RS256 + JWKS.
* Access token 15 min, refresh 7 days **[DEMO]**. Claims: `sub` (user id), `email`, `roles`,
  `departmentId`, `applicantId`, `jti`, `iat`, `exp`. Permissions are **not** in the token — they are
  resolved per request from `role_permission`, so revoking a grant takes effect immediately instead of
  after token expiry.
* BCrypt (strength 10) password hashing. No password ever logged, never in a DTO.
* `SecurityFilterChain`: stateless, CSRF off, `/api/auth/**` + `/v3/api-docs/**` +
  `/swagger-ui/**` + `/actuator/health` public, everything else authenticated.
* `status = BLOCKED|DISABLED` → `403` at the filter, before any business code (spec 16.3).
* `401` from `AuthenticationEntryPoint`, `403` from `AccessDeniedHandler`, both emitting the standard
  error body of `API_SPEC.md` §9.

## 2. Roles (spec 3.1–3.9)

| Role | Spec | Core capability | Explicitly cannot |
|---|---|---|---|
| `APPLICANT` | 3.1 | create/submit own applications, see own case tracking, endorse when named a participant (13.6) | choose processing mode (1.9, 4.3, 15.4); see internal stages, executors, internal comments, calculated price, performed works (15.13) |
| `HEAD_OF_CERTIFICATION_BODY` | 3.2 | review final package, return it, **sign** (14.4), name endorsement participants (16.9) | assign specialists inside departments (that is 3.3) |
| `DEPARTMENT_HEAD` | 3.3 | assign/reassign specialists (5.5), approve or return their results, push the stage forward | sign final documents; touch finance |
| `SPECIALIST` | 3.4 | primary check, execution work, submit result versions, prepare documents | act on tasks not assigned to them (7.5.6) |
| `ACCOUNTANT` | 3.6 | mode, price, contract, payment state | change expert/execution results (3.6, explicit) |
| `OPERATOR` | 3.8 | monitor cases, deadlines, stages; internal organisational comments | expert decisions, signing, editing results, confirming payment, changing the route (17.5) |
| `ADMIN` | 3.9, 16.17 | users, roles, permissions, departments, positions, services, routes, reference data, templates | expert/financial/signing decisions on cases; direct read/export of financial and commercial data (16.18) |

**The single most defensible decision here:** 3.2 and 3.3 are separate roles. Merging them (as the
original plan did) makes spec 16.4's distinction between `ENDORSE`, `APPROVE` and `SIGN` unrepresentable.

## 3. Permissions (spec 16.4)

`Permission = (section, action)`, `code = "SECTION:ACTION"`. Actions are exactly seven:
`VIEW, CREATE, EDIT, ENDORSE, APPROVE, SIGN, BLOCK`. Sections: `APPLICATION, CASE, PRIMARY_CHECK,
TASK, DOCUMENT, APPROVAL, FINANCE, PERFORMED_WORK, WORKFLOW_CONFIG, USER_ADMIN, REFERENCE_DATA,
REPORTING, AUDIT`. The full grant matrix is seeded in `V2__security_and_org.sql` and is the
authoritative version of the table below.

| Section | APPLICANT | ACCOUNTANT | HEAD_OF_CB | DEPT_HEAD | SPECIALIST | OPERATOR | ADMIN |
|---|---|---|---|---|---|---|---|
| APPLICATION | V, C | — | — | — | — | V | — |
| CASE | V | V | V | V | V | V | **—** |
| PRIMARY_CHECK | — | — | — | — | V, C | — | — |
| TASK | — | — | V | V, C, E, **A** | V, E | V | **—** |
| DOCUMENT | V | V | V, **A** | V, C, E | V, C, E | — | — |
| APPROVAL | **N** | **N** | N, **A** | N | N | — | — |
| FINANCE | — | V, C, E, **A** | — | — | — | — | **—** |
| PERFORMED_WORK | — | V | V | V | — | — | — |
| WORKFLOW_CONFIG | — | — | — | — | — | — | V, C, E |
| USER_ADMIN | — | — | — | — | — | — | V, C, E, **B** |
| REFERENCE_DATA | — | — | — | — | — | — | V, C, E |
| REPORTING | — | — | V | V | — | V | V |
| AUDIT | — | — | V | — | — | — | V |
| **SIGN** | — | — | **DOCUMENT:SIGN** | — | — | — | — |

`V`=VIEW `C`=CREATE `E`=EDIT `N`=ENDORSE `A`=APPROVE `B`=BLOCK.
`DOCUMENT:SIGN` exists on exactly one role. `ADMIN` holds no `CASE:*`, `TASK:*`, `FINANCE:*` —
that is spec 16.17 rendered as data.

Enforcement: `@PreAuthorize("hasAuthority('TASK:EDIT')")` on the controller method, where authorities
are the permission codes resolved by `PermissionAuthorityResolver` on each request.

## 4. Position is not a permission (spec 16.13)

`Position` carries no grants. Role, position and department are three independent columns on `app_user`.
Changing `position_id` cannot change what a user may do — test S-11 asserts exactly that, because the
spec calls it out explicitly and it is the kind of requirement that silently rots.

## 5. Object-level authorization

One domain service, `CaseAccessPolicy`, called **explicitly** from application services (not via an
aspect — explicit calls are readable in a code review and provable in a test):

```
canViewCase(principal, case):
  APPLICANT              -> case.applicantId == principal.applicantId          # spec 3.1
  ACCOUNTANT             -> true (finance projection only)                     # spec 3.6
  HEAD_OF_CB             -> true                                               # spec 3.2
  DEPARTMENT_HEAD        -> case.mainResponsibleDepartmentId == principal.departmentId
                            OR principal.departmentId in case.participatingDepartments
                            OR exists task(case, assignedDepartment = principal.departmentId)
  SPECIALIST             -> exists task(case, assignedUser = principal)
                            OR exists task(case, assignedDepartment = principal.departmentId)  # spec 3.4
  OPERATOR               -> true (monitoring, read-only)                       # spec 17.1
  ADMIN                  -> false                                              # spec 16.17
```

```
canActOnTask(principal, task, action):
  ASSIGN/REASSIGN -> DEPARTMENT_HEAD and principal.departmentId == task.assignedDepartmentId
  START/SUBMIT/COMPLETE -> principal.id == task.assignedUserId
  APPROVE/RETURN  -> DEPARTMENT_HEAD of task.assignedDepartmentId
                     OR HEAD_OF_CB when the stage type is FINAL_REVIEW
  VIEW            -> assignee, dept head of that department, HEAD_OF_CB, OPERATOR,
                     dept head of the main responsible department
```

```
canDecideApproval(principal, approvalTask):
  kind USER       -> principal.id == participantUserId
  kind DEPARTMENT -> principal.departmentId == participantDepartmentId and principal has APPROVAL:ENDORSE
  kind APPLICANT  -> principal.applicantId == round.case.applicantId          # spec 13.6
  kind ACCOUNTING -> principal has role ACCOUNTANT
```

Repository-level defence in depth: applicant-facing queries take `applicantId` as a **bound parameter
in the query**, not as a post-filter, so a forgotten check cannot leak rows.

## 6. `403` vs `404` — a deliberate choice

An applicant requesting a case that is not theirs gets **404 RESOURCE_NOT_FOUND**. Returning `403` would
confirm that a case with that ID exists, which is an enumeration oracle across tenants.
A staff member who legitimately operates inside the system but lacks a permission or department scope
gets **403** — they already know the case exists, and a `404` there would just make the product feel
broken. Documented because an interviewer will ask why the two are inconsistent; the inconsistency is
the point.

## 7. Applicant data minimisation (spec 4.19, 15.5, 15.9, 15.13)

Applicant-facing responses come from `ApplicantTrackingMapper`, a **separate mapper over a separate
projection**, never from `CaseResponse` with fields nulled out. It exposes only external stage,
contract number/date/actual amount, payment status/confirmed/debt, return-for-correction info,
notifications and the signed final document.

Never exposed to an applicant: internal stage names and internal status labels, executor identities,
department routing, internal comments (13.5.1), the CRM-calculated price and its change history (15.9),
performed-works records and bonus basis (15.13), audit log.

Test S-07 asserts on the serialized JSON body, not on the mapper, so adding a field to `CaseResponse`
later cannot silently leak it.

## 8. Admin restrictions (spec 16.17, 16.18)

Three mechanisms:
1. **No grants.** `ADMIN` has no `CASE:*`, `TASK:*`, `FINANCE:*`, `DOCUMENT:SIGN`. A 403 comes from the
   permission check, not from a special case in code.
2. **Policy.** `canViewCase` returns `false` for `ADMIN`. Belt and braces, because a future
   mis-configured grant should still not open case data.
3. **Mandatory logging.** Where an admin *does* legitimately read commercial configuration
   (`/admin/price-rules`, and any endpoint whose response contains contract sums), a
   `ConfidentialAccessAuditInterceptor` writes `CONFIDENTIAL_DATA_ACCESSED` with the endpoint, the
   filters used and the row count. Spec 16.18 requires this "в обязательном порядке", so it is an
   interceptor, not a call the developer can forget.

Bulk export of financial tables is not exposed at all in this slice — stated in the README rather than
half-implemented.

## 9. Audit as a security control (spec 20.3)

See `DATABASE_SCHEMA.md` §"Audit strategy". Security-relevant summary: the application's DB role cannot
`UPDATE` or `DELETE` `audit_log`; a trigger blocks it even if the grant were wrong; the hash chain makes
superuser tampering detectable. `GET /audit/integrity` is the demo hook — running it live in the
interview is more convincing than any slide.

## 10. Out of scope, stated openly

Field-work security (7.5.x): GPS capture, photo provenance, graphic signature, offline Late Upload,
file integrity hashing beyond `document_version.content_hash`. Database-at-rest encryption (20.1).
TLS termination and strong integration auth for Didox / Single Window / customs API (20.2) — the
outbound port interfaces exist, the transport does not. Refresh-token rotation and reuse detection.
Rate limiting and account lockout after failed logins.
