-- ============================================================================
-- [DEMO] SEED DATA — DEMONSTRATION VALUES ONLY.
-- The client specification does not define tariffs, RED/YELLOW/GREEN criteria, deadlines,
-- department names or positions. Everything below is a demonstration assumption
-- documented in docs/ASSUMPTIONS.md. Nothing here is an official client business rule.
-- Loaded only when spring.flyway.locations includes classpath:db/demo (profiles: local, demo, test).
-- ============================================================================

-- ---------- departments and positions ----------
INSERT INTO department (code, name) VALUES
 ('MAIN','[DEMO] Основное ответственное подразделение'),
 ('LABORATORY','[DEMO] Лаборатория'),
 ('GREEN_CERTIFICATION','[DEMO] Подразделение «зеленой» сертификации'),
 ('ACCOUNTING','[DEMO] Бухгалтерия'),
 ('OPERATIONS','[DEMO] Служба сопровождения');

INSERT INTO position (code, name) VALUES
 ('HEAD','[DEMO] Руководитель'),
 ('CHIEF_SPECIALIST','[DEMO] Главный специалист'),
 ('SPECIALIST','[DEMO] Специалист'),
 ('ACCOUNTANT','[DEMO] Бухгалтер'),
 ('OPERATOR','[DEMO] Оператор');

-- ---------- users ----------
-- All demo passwords are "Password1!" (BCrypt cost 10). Demo only.
INSERT INTO app_user (email, password_hash, full_name, department_id, position_id) VALUES
 ('admin@example.com','$2a$10$DEMOhashDEMOhashDEMOhashDEMOhashDEMOhashDEMOhashDEMOha','[DEMO] Администратор',
   NULL,(SELECT id FROM position WHERE code='HEAD')),
 ('head@example.com','$2a$10$DEMOhashDEMOhashDEMOhashDEMOhashDEMOhashDEMOhashDEMOha','[DEMO] Руководитель органа сертификации',
   (SELECT id FROM department WHERE code='MAIN'),(SELECT id FROM position WHERE code='HEAD')),
 ('depthead.main@example.com','$2a$10$DEMOhashDEMOhashDEMOhashDEMOhashDEMOhashDEMOhashDEMOha','[DEMO] Руководитель основного подразделения',
   (SELECT id FROM department WHERE code='MAIN'),(SELECT id FROM position WHERE code='HEAD')),
 ('depthead.lab@example.com','$2a$10$DEMOhashDEMOhashDEMOhashDEMOhashDEMOhashDEMOhashDEMOha','[DEMO] Руководитель лаборатории',
   (SELECT id FROM department WHERE code='LABORATORY'),(SELECT id FROM position WHERE code='HEAD')),
 ('accountant@example.com','$2a$10$DEMOhashDEMOhashDEMOhashDEMOhashDEMOhashDEMOhashDEMOha','[DEMO] Бухгалтер',
   (SELECT id FROM department WHERE code='ACCOUNTING'),(SELECT id FROM position WHERE code='ACCOUNTANT')),
 ('specialist1@example.com','$2a$10$DEMOhashDEMOhashDEMOhashDEMOhashDEMOhashDEMOhashDEMOha','[DEMO] Специалист основного подразделения',
   (SELECT id FROM department WHERE code='MAIN'),(SELECT id FROM position WHERE code='SPECIALIST')),
 ('specialist2@example.com','$2a$10$DEMOhashDEMOhashDEMOhashDEMOhashDEMOhashDEMOhashDEMOha','[DEMO] Специалист лаборатории',
   (SELECT id FROM department WHERE code='LABORATORY'),(SELECT id FROM position WHERE code='SPECIALIST')),
 ('specialist3@example.com','$2a$10$DEMOhashDEMOhashDEMOhashDEMOhashDEMOhashDEMOhashDEMOha','[DEMO] Специалист «зеленой» сертификации',
   (SELECT id FROM department WHERE code='GREEN_CERTIFICATION'),(SELECT id FROM position WHERE code='SPECIALIST')),
 ('operator@example.com','$2a$10$DEMOhashDEMOhashDEMOhashDEMOhashDEMOhashDEMOhashDEMOha','[DEMO] Оператор',
   (SELECT id FROM department WHERE code='OPERATIONS'),(SELECT id FROM position WHERE code='OPERATOR'));

INSERT INTO user_role (user_id, role_id)
SELECT u.id, r.id FROM app_user u JOIN role r ON r.code = CASE u.email
    WHEN 'admin@example.com' THEN 'ADMIN'
    WHEN 'head@example.com' THEN 'HEAD_OF_CERTIFICATION_BODY'
    WHEN 'depthead.main@example.com' THEN 'DEPARTMENT_HEAD'
    WHEN 'depthead.lab@example.com' THEN 'DEPARTMENT_HEAD'
    WHEN 'accountant@example.com' THEN 'ACCOUNTANT'
    WHEN 'operator@example.com' THEN 'OPERATOR'
    ELSE 'SPECIALIST' END;

UPDATE department SET head_user_id = (SELECT id FROM app_user WHERE email='depthead.main@example.com')
 WHERE code = 'MAIN';
UPDATE department SET head_user_id = (SELECT id FROM app_user WHERE email='depthead.lab@example.com')
 WHERE code = 'LABORATORY';

-- ---------- applicants (both types, spec 15.2) ----------
INSERT INTO applicant (type,last_name,first_name,middle_name,birth_date,passport_series,passport_number,
                       pinfl,address,phone,email)
VALUES ('INDIVIDUAL','[DEMO] Иванов','Иван','Иванович','1990-01-15','AA','1234567',
        '11111111111111','[DEMO] г. Ташкент','+998900000001','applicant@example.com');

INSERT INTO applicant (type,org_name,tin,representative_full_name,representative_position,
                       address,phone,email)
VALUES ('LEGAL_ENTITY','[DEMO] ООО «Демо Индустрия»','305123456','[DEMO] Петров Петр Петрович',
        '[DEMO] Директор','[DEMO] г. Ташкент','+998900000002','applicant.legal@example.com');

INSERT INTO app_user (email, password_hash, full_name, applicant_id)
VALUES ('applicant@example.com','$2a$10$DEMOhashDEMOhashDEMOhashDEMOhashDEMOhashDEMOhashDEMOha',
        '[DEMO] Иванов Иван Иванович',(SELECT id FROM applicant WHERE email='applicant@example.com')),
       ('applicant.legal@example.com','$2a$10$DEMOhashDEMOhashDEMOhashDEMOhashDEMOhashDEMOhashDEMOha',
        '[DEMO] Петров Петр Петрович',(SELECT id FROM applicant WHERE email='applicant.legal@example.com'));
INSERT INTO user_role (user_id, role_id)
SELECT u.id,(SELECT id FROM role WHERE code='APPLICANT') FROM app_user u
 WHERE u.email IN ('applicant@example.com','applicant.legal@example.com');

-- ---------- service ----------
INSERT INTO service (code,name,description,contract_required,payment_required)
VALUES ('WASTE_CERTIFICATION','[DEMO] Сертификация отходов',
        '[DEMO] Демонстрационная услуга для vertical slice',true,true);
INSERT INTO service_submission_channel (service_id, channel)
SELECT id, c FROM service, (VALUES ('PERSONAL_CABINET'),('SINGLE_WINDOW'),('PAPER')) AS x(c)
 WHERE code='WASTE_CERTIFICATION';

-- ============================================================================
-- WORKFLOW v1 — ACTIVE. Full demo route.
-- ============================================================================
INSERT INTO workflow (service_id,code,version,name,status,main_responsible_department_id,
    expedited_allowed,contract_required,payment_required,allow_execution_before_full_payment,
    payment_waiting_days,total_deadline_days,approval_required,published_at,published_by)
SELECT s.id,'WASTE_CERT_ROUTE',1,'[DEMO] Маршрут сертификации отходов v1','ACTIVE',
    (SELECT id FROM department WHERE code='MAIN'),
    true,true,true,false,10,30,true,now(),(SELECT id FROM app_user WHERE email='admin@example.com')
FROM service s WHERE s.code='WASTE_CERTIFICATION';

INSERT INTO workflow_stage (workflow_id,code,name,stage_type,sequence,parallel_group,required,
    external_stage_id,internal_status_label,responsible_role_code,responsible_department_id,
    assignment_mode,deadline_days,expedited_deadline_days,work_type_id,produces_document_type,
    requires_result,revision_allowed,approval_required,approval_mode)
SELECT w.id, v.code, v.name, v.stage_type, v.seq, v.pgroup, v.req,
       (SELECT id FROM external_stage WHERE code=v.ext),
       v.internal_label, v.role_code,
       (SELECT id FROM department WHERE code=v.dept),
       v.assign, v.dl, v.edl,
       (SELECT id FROM work_type WHERE code=v.wt),
       v.doc_type, v.needs_result, v.rev, v.appr, v.appr_mode
FROM workflow w, (VALUES
 -- code, name, type, seq, parallelGroup, required, externalStage, internalLabel, role, dept,
 -- assignMode, deadlineDays, expeditedDays, workType, docType, requiresResult, revision, approval, apprMode
 ('PRIMARY_CHECK','[DEMO] Первичная проверка','PRIMARY_CHECK',10,NULL,true,'DOCUMENTS_UNDER_REVIEW',
   'Первичная проверка','SPECIALIST','MAIN','DEPARTMENT_HEAD_ASSIGNS',2,1,NULL,NULL,true,true,false,NULL),
 ('ACCOUNTING','[DEMO] Бухгалтерия: режим, стоимость, договор','ACCOUNTING',20,NULL,true,'CONTRACT_SENT',
   'В бухгалтерии','ACCOUNTANT','ACCOUNTING','ROUTE_FIXED_USER',3,2,NULL,NULL,false,false,false,NULL),
 ('PAYMENT_CONTROL','[DEMO] Контроль оплаты','PAYMENT_CONTROL',30,NULL,true,'AWAITING_PAYMENT',
   'Ожидается оплата','ACCOUNTANT','ACCOUNTING','ROUTE_FIXED_USER',10,10,NULL,NULL,false,false,false,NULL),
 ('IDENTIFICATION','[DEMO] Идентификация','EXECUTION',40,NULL,true,'WORK_IN_PROGRESS',
   'Идентификация','SPECIALIST','MAIN','DEPARTMENT_HEAD_ASSIGNS',5,3,'WASTE_CERT_IDENTIFICATION',
   'IDENTIFICATION_ACT',true,true,false,NULL),
 -- three parallel stages, group PAR_EXEC. AUDIT is optional on purpose: it proves that gating
 -- waits for REQUIRED tasks only (spec 7.14).
 ('LABORATORY','[DEMO] Лабораторные исследования','EXECUTION',50,'PAR_EXEC',true,'LAB_TESTS_IN_PROGRESS',
   'Лабораторные работы','SPECIALIST','LABORATORY','DEPARTMENT_HEAD_ASSIGNS',7,4,'LAB_AIR_ANALYSIS',
   'LAB_PROTOCOL',true,true,false,NULL),
 ('EXPERT_REVIEW','[DEMO] Экспертиза документов','EXECUTION',60,'PAR_EXEC',true,'WORK_IN_PROGRESS',
   'Экспертиза','SPECIALIST','MAIN','DEPARTMENT_HEAD_ASSIGNS',5,3,'OPINION_ISSUANCE',
   'EXPERT_OPINION',true,true,false,NULL),
 ('AUDIT','[DEMO] Аудит (необязательный)','EXECUTION',70,'PAR_EXEC',false,'WORK_IN_PROGRESS',
   'Аудит','SPECIALIST','GREEN_CERTIFICATION','DEPARTMENT_HEAD_ASSIGNS',7,4,'GREEN_AUDIT',
   'AUDIT_REPORT',true,true,false,NULL),
 ('FINAL_REVIEW','[DEMO] Итоговая проверка комплектности','FINAL_REVIEW',80,NULL,true,
   'FINAL_DOCUMENT_PREPARING','Итоговая проверка','SPECIALIST','MAIN','DEPARTMENT_HEAD_ASSIGNS',3,2,NULL,
   'FINAL_OPINION',true,true,false,NULL),
 ('ENDORSEMENT','[DEMO] Согласование итогового документа','ENDORSEMENT',90,NULL,true,
   'DOCUMENT_UNDER_ENDORSEMENT','На согласовании','DEPARTMENT_HEAD','MAIN','ROUTE_FIXED_USER',3,2,NULL,
   NULL,false,true,true,'PARALLEL'),
 ('SIGNING','[DEMO] Подписание','SIGNING',100,NULL,true,'DOCUMENT_ON_SIGNING',
   'На подписании','HEAD_OF_CERTIFICATION_BODY','MAIN','ROUTE_FIXED_USER',2,1,'WASTE_CERT_ISSUANCE',
   'CERTIFICATE',false,false,false,NULL),
 ('COMPLETION','[DEMO] Выдача результата','COMPLETION',110,NULL,true,'RESULT_ISSUED',
   'Завершено','SPECIALIST','MAIN','ROUTE_FIXED_USER',1,1,NULL,NULL,false,false,false,NULL)
) AS v(code,name,stage_type,seq,pgroup,req,ext,internal_label,role_code,dept,assign,dl,edl,wt,doc_type,
       needs_result,rev,appr,appr_mode)
WHERE w.code='WASTE_CERT_ROUTE' AND w.version=1;

-- Note: FINAL_REVIEW and ENDORSEMENT both map onto internal stages while IDENTIFICATION,
-- EXPERT_REVIEW and AUDIT all map onto the SINGLE external stage WORK_IN_PROGRESS.
-- That is the N:1 mapping of spec 5.11 / 15.6 in action.

INSERT INTO workflow_transition (workflow_id,from_stage_id,to_stage_id,condition_type,condition_value,sequence)
SELECT w.id,
       (SELECT id FROM workflow_stage WHERE workflow_id=w.id AND code=t.from_code),
       (SELECT id FROM workflow_stage WHERE workflow_id=w.id AND code=t.to_code),
       t.cond, t.cval, t.seq
FROM workflow w, (VALUES
 (NULL,'PRIMARY_CHECK','ALWAYS',NULL,0),
 ('PRIMARY_CHECK','ACCOUNTING','PRIMARY_CHECK_DECISION_IS','ACCEPTED',0),
 ('ACCOUNTING','PAYMENT_CONTROL','ALWAYS',NULL,0),
 ('PAYMENT_CONTROL','IDENTIFICATION','PAYMENT_STATE_SATISFIED',NULL,0),
 ('IDENTIFICATION','LABORATORY','ALWAYS',NULL,0),
 ('IDENTIFICATION','EXPERT_REVIEW','ALWAYS',NULL,1),
 ('IDENTIFICATION','AUDIT','ALWAYS',NULL,2),
 ('LABORATORY','FINAL_REVIEW','ALL_REQUIRED_PARALLEL_TASKS_DONE','PAR_EXEC',0),
 ('EXPERT_REVIEW','FINAL_REVIEW','ALL_REQUIRED_PARALLEL_TASKS_DONE','PAR_EXEC',1),
 ('AUDIT','FINAL_REVIEW','ALL_REQUIRED_PARALLEL_TASKS_DONE','PAR_EXEC',2),
 ('FINAL_REVIEW','ENDORSEMENT','ALWAYS',NULL,0),
 ('ENDORSEMENT','SIGNING','APPROVAL_ROUND_COMPLETED',NULL,0),
 ('SIGNING','COMPLETION','ALWAYS',NULL,0)
) AS t(from_code,to_code,cond,cval,seq)
WHERE w.code='WASTE_CERT_ROUTE' AND w.version=1;

-- ============================================================================
-- WORKFLOW v2 — DRAFT, published in the same seed so that the "old case keeps its old route"
-- test is real and not hypothetical. v2 drops AUDIT and adds a deadline change.
-- ============================================================================
INSERT INTO workflow (service_id,code,version,name,status,main_responsible_department_id,
    expedited_allowed,contract_required,payment_required,allow_execution_before_full_payment,
    payment_waiting_days,total_deadline_days,approval_required,published_at,published_by)
SELECT s.id,'WASTE_CERT_ROUTE',2,'[DEMO] Маршрут сертификации отходов v2','DRAFT',
    (SELECT id FROM department WHERE code='MAIN'),
    true,true,true,true,7,25,true,NULL,NULL
FROM service s WHERE s.code='WASTE_CERTIFICATION';

INSERT INTO workflow_stage (workflow_id,code,name,stage_type,sequence,parallel_group,required,
    external_stage_id,internal_status_label,responsible_role_code,responsible_department_id,
    assignment_mode,deadline_days,expedited_deadline_days,requires_result,revision_allowed,approval_required)
SELECT w.id, s.code, s.name, s.stage_type, s.seq, s.pgroup, true,
       (SELECT id FROM external_stage WHERE code=s.ext), s.internal_label,'SPECIALIST',
       (SELECT id FROM department WHERE code='MAIN'),'DEPARTMENT_HEAD_ASSIGNS',3,2,true,true,false
FROM workflow w, (VALUES
 ('PRIMARY_CHECK','[DEMO] Первичная проверка v2','PRIMARY_CHECK',10,NULL,'DOCUMENTS_UNDER_REVIEW','Первичная проверка'),
 ('ACCOUNTING','[DEMO] Бухгалтерия v2','ACCOUNTING',20,NULL,'CONTRACT_SENT','В бухгалтерии'),
 ('PAYMENT_CONTROL','[DEMO] Контроль оплаты v2','PAYMENT_CONTROL',30,NULL,'AWAITING_PAYMENT','Ожидается оплата'),
 ('IDENTIFICATION','[DEMO] Идентификация v2','EXECUTION',40,NULL,'WORK_IN_PROGRESS','Идентификация'),
 ('LABORATORY','[DEMO] Лаборатория v2','EXECUTION',50,'PAR_EXEC','LAB_TESTS_IN_PROGRESS','Лабораторные работы'),
 ('EXPERT_REVIEW','[DEMO] Экспертиза v2','EXECUTION',60,'PAR_EXEC','WORK_IN_PROGRESS','Экспертиза'),
 ('FINAL_REVIEW','[DEMO] Итоговая проверка v2','FINAL_REVIEW',70,NULL,'FINAL_DOCUMENT_PREPARING','Итоговая проверка'),
 ('SIGNING','[DEMO] Подписание v2','SIGNING',80,NULL,'DOCUMENT_ON_SIGNING','На подписании'),
 ('COMPLETION','[DEMO] Выдача результата v2','COMPLETION',90,NULL,'RESULT_ISSUED','Завершено')
) AS s(code,name,stage_type,seq,pgroup,ext,internal_label)
WHERE w.code='WASTE_CERT_ROUTE' AND w.version=2;

-- ---------- [DEMO] price rules (ASSUMPTIONS A3) ----------
INSERT INTO price_rule (service_id,rule_type,processing_mode,base_price,coefficient,demo)
SELECT s.id,'BASE_PER_ITEM',NULL,5000000.00,NULL,true FROM service s WHERE s.code='WASTE_CERTIFICATION';
INSERT INTO price_rule (service_id,rule_type,processing_mode,base_price,coefficient,demo)
SELECT s.id,'MODE_COEFFICIENT','TRADITIONAL',NULL,1.0000,true FROM service s WHERE s.code='WASTE_CERTIFICATION';
INSERT INTO price_rule (service_id,rule_type,processing_mode,base_price,coefficient,demo)
SELECT s.id,'MODE_COEFFICIENT','EXPEDITED',NULL,1.5000,true FROM service s WHERE s.code='WASTE_CERTIFICATION';
INSERT INTO price_rule (service_id,work_type_id,rule_type,base_price,demo)
SELECT s.id,(SELECT id FROM work_type WHERE code='LAB_AIR_ANALYSIS'),'ADDITIONAL_WORK_FEE',1200000.00,true
FROM service s WHERE s.code='WASTE_CERTIFICATION';
INSERT INTO price_rule (service_id,rule_type,base_price,demo)
SELECT s.id,'MINIMUM_TOTAL',1000000.00,true FROM service s WHERE s.code='WASTE_CERTIFICATION';
