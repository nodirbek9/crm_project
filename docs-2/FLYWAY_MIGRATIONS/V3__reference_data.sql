-- V3: reference data (spec 5.2, 8.2, 15.7, 16.8)

CREATE TABLE service (
    id                     uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code                   varchar(60) NOT NULL,
    name                   varchar(255) NOT NULL,
    description            text,
    active                 boolean NOT NULL DEFAULT true,
    contract_required      boolean NOT NULL DEFAULT true,
    payment_required       boolean NOT NULL DEFAULT true,
    standalone_laboratory  boolean NOT NULL DEFAULT false,  -- spec 9.1/9.2
    created_at             timestamptz NOT NULL DEFAULT now(),
    updated_at             timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_service_code UNIQUE (code)
);

CREATE TABLE service_submission_channel (
    service_id uuid NOT NULL REFERENCES service(id) ON DELETE CASCADE,
    channel    varchar(30) NOT NULL,
    PRIMARY KEY (service_id, channel),
    CONSTRAINT ck_service_channel CHECK (channel IN
        ('PERSONAL_CABINET','SINGLE_WINDOW','OTHER_SERVICE','PAPER'))   -- spec 1.3
);

-- spec 8.2: the matrix is an INITIAL list, so it must be a configurable table, not an enum.
CREATE TABLE work_type (
    id                                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code                              varchar(60) NOT NULL,
    name                              varchar(255) NOT NULL,
    service_scope                     varchar(120),
    stage_kind                        varchar(120),
    requires_contract_amount_bracket  boolean NOT NULL DEFAULT false,  -- spec 8.4
    basis_document_description        varchar(255),                    -- matrix "Основание для расчета"
    active                            boolean NOT NULL DEFAULT true,
    CONSTRAINT uq_work_type_code UNIQUE (code)
);

-- spec 5.11 / 15.6: several internal stages may collapse into ONE external stage.
CREATE TABLE external_stage (
    id                 uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code               varchar(60) NOT NULL,
    name_for_applicant varchar(255) NOT NULL,
    sequence           int NOT NULL,
    active             boolean NOT NULL DEFAULT true,
    CONSTRAINT uq_external_stage_code UNIQUE (code)
);

CREATE TRIGGER tr_service_updated BEFORE UPDATE ON service
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- [DEMO] external stage labels taken verbatim from spec 15.7
INSERT INTO external_stage (code, name_for_applicant, sequence) VALUES
 ('REGISTERED','Заявка зарегистрирована',10),
 ('DOCUMENTS_UNDER_REVIEW','Документы проверяются',20),
 ('CONTRACT_SENT','Договор направлен',30),
 ('AWAITING_PAYMENT','Ожидается оплата',40),
 ('WORK_IN_PROGRESS','Выполняются работы',50),
 ('LAB_TESTS_IN_PROGRESS','Проводятся лабораторные исследования',60),
 ('DOCUMENT_UNDER_ENDORSEMENT','Документ на согласовании',70),
 ('FINAL_DOCUMENT_PREPARING','Подготавливается итоговый документ',80),
 ('DOCUMENT_ON_SIGNING','Документ на подписании',90),
 ('RESULT_ISSUED','Результат выдан',100),
 ('RETURNED_FOR_CORRECTION','Возвращено на доработку',110),
 ('REJECTED','Отказано',120);

-- [DEMO] work types seeded from the specification's performed-works matrix (section 8.2)
INSERT INTO work_type (code, name, service_scope, stage_kind, requires_contract_amount_bracket, basis_document_description) VALUES
 ('TECH_SPEC_AQMS','Разработка технических условий — AQMS','TECH_SPEC','TECH_DOCUMENT',false,'Утвержденный технический документ и счет-фактура'),
 ('TECH_SPEC_CEMS','Разработка технических условий — CEMS','TECH_SPEC','TECH_DOCUMENT',false,'Утвержденный технический документ и счет-фактура'),
 ('TECH_SPEC_PGOU','Разработка технических условий — ПГОУ','TECH_SPEC','TECH_DOCUMENT',false,'Утвержденный технический документ и счет-фактура'),
 ('TECH_SPEC_LOS','Разработка технических условий — ЛОС','TECH_SPEC','TECH_DOCUMENT',false,'Утвержденный технический документ и счет-фактура'),
 ('WASTE_CERT_IDENTIFICATION','Сертификация отходов — идентификация','WASTE_CERTIFICATION','IDENTIFICATION',false,'Экологический сертификат и счет-фактура'),
 ('WASTE_CERT_ISSUANCE','Сертификация отходов — оформление сертификата','WASTE_CERTIFICATION','CERTIFICATE',false,'Экологический сертификат и счет-фактура'),
 ('EXPEDITED_IDENTIFICATION','Ускоренный режим — идентификация','EXPEDITED_CERTIFICATION','IDENTIFICATION',false,'Экологический сертификат и счет-фактура'),
 ('EXPEDITED_LAB_WORK','Ускоренный режим — лабораторные работы','EXPEDITED_CERTIFICATION','LABORATORY',false,'Экологический сертификат и счет-фактура'),
 ('EXPEDITED_CERT_ISSUANCE','Ускоренный режим — оформление сертификата','EXPEDITED_CERTIFICATION','CERTIFICATE',false,'Экологический сертификат и счет-фактура'),
 ('OPINION_IDENTIFICATION','Оформление заключений — идентификация','OPINION','IDENTIFICATION',false,'Экологическое заключение и счет-фактура'),
 ('OPINION_ISSUANCE','Оформление заключений — заключение','OPINION','OPINION',false,'Экологическое заключение и счет-фактура'),
 ('CONFORMITY_IDENTIFICATION','Сертификат соответствия — идентификация','CONFORMITY','IDENTIFICATION',false,'Сертификат соответствия и счет-фактура'),
 ('CONFORMITY_ISSUANCE','Сертификат соответствия — оформление сертификата','CONFORMITY','CERTIFICATE',false,'Сертификат соответствия и счет-фактура'),
 ('GREEN_DOC_EXPERTISE','Экспертиза документов для «зеленого» сертификата','GREEN_CERTIFICATION','EXPERTISE',true,'«Зеленый» сертификат либо решение об отказе и счет-фактура'),
 ('GREEN_AUDIT','Аудит «зеленого» сертификата','GREEN_CERTIFICATION','AUDIT',true,'«Зеленый» сертификат либо решение об отказе и счет-фактура'),
 ('LAB_AIR_SAMPLING','Лабораторные анализы атмосферного воздуха — отбор проб','LABORATORY','SAMPLING',false,'Оформленный протокол и счет-фактура'),
 ('LAB_AIR_ANALYSIS','Лабораторные анализы атмосферного воздуха — анализ и протокол','LABORATORY','ANALYSIS',false,'Оформленный протокол и счет-фактура'),
 ('LAB_WASTEWATER_SAMPLING','Лабораторные анализы сточных вод — отбор проб','LABORATORY','SAMPLING',false,'Оформленный протокол и счет-фактура'),
 ('LAB_WASTEWATER_ANALYSIS','Лабораторные анализы сточных вод — анализ и протокол','LABORATORY','ANALYSIS',false,'Оформленный протокол и счет-фактура');
