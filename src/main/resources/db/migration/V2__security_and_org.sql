-- V2: security and organisational structure (spec 3.x, 16.3, 16.4, 16.12, 16.13)

CREATE TABLE permission (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    section     varchar(40) NOT NULL,
    action      varchar(20) NOT NULL,
    code        varchar(64) NOT NULL,
    description varchar(255),
    CONSTRAINT uq_permission_code UNIQUE (code),
    CONSTRAINT uq_permission_section_action UNIQUE (section, action),
    -- spec 16.4 fixes exactly seven actions
    CONSTRAINT ck_permission_action CHECK (action IN
        ('VIEW','CREATE','EDIT','ENDORSE','APPROVE','SIGN','BLOCK')),
    CONSTRAINT ck_permission_section CHECK (section IN
        ('APPLICATION','CASE','PRIMARY_CHECK','TASK','DOCUMENT','APPROVAL','FINANCE',
         'PERFORMED_WORK','WORKFLOW_CONFIG','USER_ADMIN','REFERENCE_DATA','REPORTING','AUDIT'))
);

CREATE TABLE role (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code        varchar(40) NOT NULL,
    name        varchar(120) NOT NULL,
    system_role boolean NOT NULL DEFAULT true,
    created_at  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_role_code UNIQUE (code),
    -- spec 3.2 and 3.3 are two distinct roles: see PLAN_REVIEW C2
    CONSTRAINT ck_role_code CHECK (code IN
        ('ADMIN','APPLICANT','ACCOUNTANT','HEAD_OF_CERTIFICATION_BODY',
         'DEPARTMENT_HEAD','SPECIALIST','OPERATOR'))
);

CREATE TABLE role_permission (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    role_id       uuid NOT NULL REFERENCES role(id) ON DELETE CASCADE,
    permission_id uuid NOT NULL REFERENCES permission(id) ON DELETE RESTRICT,
    granted_at    timestamptz NOT NULL DEFAULT now(),
    granted_by    uuid,
    CONSTRAINT uq_role_permission UNIQUE (role_id, permission_id)
);
CREATE INDEX ix_role_permission_role ON role_permission(role_id);

CREATE TABLE department (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code         varchar(40) NOT NULL,
    name         varchar(200) NOT NULL,
    parent_id    uuid REFERENCES department(id) ON DELETE RESTRICT,
    head_user_id uuid,                     -- FK added after app_user exists
    active       boolean NOT NULL DEFAULT true,
    created_at   timestamptz NOT NULL DEFAULT now(),
    updated_at   timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_department_code UNIQUE (code),
    CONSTRAINT ck_department_not_self_parent CHECK (parent_id IS NULL OR parent_id <> id)
);
CREATE INDEX ix_department_parent ON department(parent_id);

CREATE TABLE position (
    id     uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code   varchar(40) NOT NULL,
    name   varchar(200) NOT NULL,
    active boolean NOT NULL DEFAULT true,
    CONSTRAINT uq_position_code UNIQUE (code)
);
-- NOTE spec 16.13: position carries no permissions by design. Changing a user's position
-- must never change what that user may do.

CREATE TABLE app_user (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    email         varchar(255) NOT NULL,
    password_hash varchar(100) NOT NULL,
    full_name     varchar(200) NOT NULL,
    department_id uuid REFERENCES department(id) ON DELETE RESTRICT,
    position_id   uuid REFERENCES position(id) ON DELETE RESTRICT,
    applicant_id  uuid,                    -- FK added in V5 (applicant created later)
    status        varchar(20) NOT NULL DEFAULT 'ACTIVE',
    version       bigint NOT NULL DEFAULT 0,
    created_at    timestamptz NOT NULL DEFAULT now(),
    updated_at    timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_app_user_email UNIQUE (email),
    CONSTRAINT ck_app_user_status CHECK (status IN ('ACTIVE','BLOCKED','DISABLED'))
);
CREATE INDEX ix_app_user_department ON app_user(department_id);
CREATE INDEX ix_app_user_applicant ON app_user(applicant_id);

ALTER TABLE department
    ADD CONSTRAINT fk_department_head FOREIGN KEY (head_user_id)
    REFERENCES app_user(id) ON DELETE SET NULL;

CREATE TABLE user_role (
    user_id uuid NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    role_id uuid NOT NULL REFERENCES role(id) ON DELETE RESTRICT,
    PRIMARY KEY (user_id, role_id)
);
CREATE INDEX ix_user_role_role ON user_role(role_id);

CREATE TRIGGER tr_department_updated BEFORE UPDATE ON department
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER tr_app_user_updated BEFORE UPDATE ON app_user
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- The seven permissions of spec 16.4 across every section.
INSERT INTO permission (section, action, code, description)
SELECT s.section, a.action, s.section || ':' || a.action,
       'spec 16.4 action ' || a.action || ' on section ' || s.section
FROM (VALUES ('APPLICATION'),('CASE'),('PRIMARY_CHECK'),('TASK'),('DOCUMENT'),('APPROVAL'),
             ('FINANCE'),('PERFORMED_WORK'),('WORKFLOW_CONFIG'),('USER_ADMIN'),
             ('REFERENCE_DATA'),('REPORTING'),('AUDIT')) AS s(section)
CROSS JOIN (VALUES ('VIEW'),('CREATE'),('EDIT'),('ENDORSE'),('APPROVE'),('SIGN'),('BLOCK')) AS a(action);

INSERT INTO role (code, name, system_role) VALUES
 ('ADMIN','Администратор системы',true),
 ('APPLICANT','Заявитель',true),
 ('ACCOUNTANT','Бухгалтерия',true),
 ('HEAD_OF_CERTIFICATION_BODY','Руководитель органа сертификации',true),
 ('DEPARTMENT_HEAD','Руководитель подразделения',true),
 ('SPECIALIST','Специалист подразделения',true),
 ('OPERATOR','Оператор системы',true);

-- Grant matrix. Derived strictly from spec 3.1-3.9, 16.17, 16.18. See SECURITY_SPEC.md.
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r JOIN permission p ON p.code IN (
    'APPLICATION:VIEW','APPLICATION:CREATE','CASE:VIEW','DOCUMENT:VIEW','APPROVAL:ENDORSE'
) WHERE r.code = 'APPLICANT';

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r JOIN permission p ON p.code IN (
    'CASE:VIEW','FINANCE:VIEW','FINANCE:CREATE','FINANCE:EDIT','FINANCE:APPROVE',
    'APPROVAL:ENDORSE','DOCUMENT:VIEW','PERFORMED_WORK:VIEW'
) WHERE r.code = 'ACCOUNTANT';
-- spec 3.6: accounting never changes expert or execution results -> no TASK:* grants.

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r JOIN permission p ON p.code IN (
    'CASE:VIEW','TASK:VIEW','DOCUMENT:VIEW','DOCUMENT:APPROVE','APPROVAL:ENDORSE',
    'APPROVAL:APPROVE','DOCUMENT:SIGN','PERFORMED_WORK:VIEW','REPORTING:VIEW','AUDIT:VIEW'
) WHERE r.code = 'HEAD_OF_CERTIFICATION_BODY';
-- spec 14.4: only this role holds DOCUMENT:SIGN.

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r JOIN permission p ON p.code IN (
    'CASE:VIEW','TASK:VIEW','TASK:CREATE','TASK:EDIT','TASK:APPROVE',
    'DOCUMENT:VIEW','DOCUMENT:CREATE','DOCUMENT:EDIT','APPROVAL:ENDORSE',
    'PERFORMED_WORK:VIEW','REPORTING:VIEW'
) WHERE r.code = 'DEPARTMENT_HEAD';
-- spec 3.3 / 5.5: TASK:EDIT is what allows assigning and reassigning executors.

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r JOIN permission p ON p.code IN (
    'CASE:VIEW','TASK:VIEW','TASK:EDIT','PRIMARY_CHECK:VIEW','PRIMARY_CHECK:CREATE',
    'DOCUMENT:VIEW','DOCUMENT:CREATE','DOCUMENT:EDIT','APPROVAL:ENDORSE'
) WHERE r.code = 'SPECIALIST';

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r JOIN permission p ON p.code IN (
    'CASE:VIEW','TASK:VIEW','APPLICATION:VIEW','REPORTING:VIEW'
) WHERE r.code = 'OPERATOR';
-- spec 17.5: no finance, no signing, no result editing, no route change.

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r JOIN permission p ON p.code IN (
    'USER_ADMIN:VIEW','USER_ADMIN:CREATE','USER_ADMIN:EDIT','USER_ADMIN:BLOCK',
    'WORKFLOW_CONFIG:VIEW','WORKFLOW_CONFIG:CREATE','WORKFLOW_CONFIG:EDIT',
    'REFERENCE_DATA:VIEW','REFERENCE_DATA:CREATE','REFERENCE_DATA:EDIT',
    'REPORTING:VIEW','AUDIT:VIEW'
) WHERE r.code = 'ADMIN';
-- spec 16.17 / 16.18: ADMIN deliberately receives NO CASE:*, TASK:*, FINANCE:*, DOCUMENT:SIGN.
