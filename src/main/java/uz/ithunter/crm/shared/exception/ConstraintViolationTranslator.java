package uz.ithunter.crm.shared.exception;

import java.util.Map;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Maps a DB unique-constraint name to a specific business error code (Phase 12 DoD:
 * "DataIntegrityViolationException -> business error code translation by constraint name").
 *
 * <p>Only {@code uq_*} constraints and partial unique indexes are mapped here, not the schema's
 * {@code ck_*} CHECK constraints. A CHECK failing means the application sent data Bean Validation
 * and the service layer should already have rejected - a genuine application bug, not a race - so
 * it is correct for it to keep surfacing as an unmapped {@code DATA_INTEGRITY_VIOLATION} (500-free
 * but generic) rather than being smoothed over into a misleading business code. UNIQUE constraints
 * are different: WORKFLOW_ENGINE_DESIGN.md 12 documents several as the deliberate last-line
 * concurrency guard for a genuine simultaneous race the service layer's own pre-checks cannot
 * fully prevent (two requests both pass the pre-check, only one INSERT/UPDATE wins at the DB).
 */
@Component
public class ConstraintViolationTranslator {

    public record Mapping(HttpStatus status, String code, String message) {
    }

    private static final Map<String, Mapping> MAPPINGS = Map.ofEntries(
            // --- Case/workflow engine races (WORKFLOW_ENGINE_DESIGN.md 12) ---
            Map.entry("uq_case_application", conflict("ALREADY_REGISTERED",
                    "This application has already been registered as a case")),
            Map.entry("uq_case_stage", conflict("STAGE_ALREADY_ACTIVATED",
                    "This stage was already activated by another request")),
            Map.entry("uq_task_case_stage", conflict("TASK_ALREADY_EXISTS_FOR_STAGE",
                    "A task already exists for this stage")),
            Map.entry("uq_task_result_live", conflict("TASK_RESULT_ALREADY_SUBMITTED",
                    "A result was already submitted for this task by another request")),
            Map.entry("uq_workflow_one_active", conflict("WORKFLOW_ALREADY_ACTIVE",
                    "Another version of this workflow was published first")),
            Map.entry("uq_payment_conf_external", conflict("DUPLICATE_PAYMENT_CONFIRMATION",
                    "A payment confirmation with this external reference was already recorded")),
            Map.entry("uq_docver_signed_once", conflict("DOCUMENT_ALREADY_SIGNED",
                    "This document version was already signed by another request")),
            Map.entry("uq_approval_round_one_open", conflict("APPROVAL_ROUND_ALREADY_OPEN",
                    "An approval round is already open for this document version")),
            Map.entry("uq_performed_work_once", conflict("PERFORMED_WORK_ALREADY_RECORDED",
                    "Performed work was already recorded for this case, work type and stage")),
            Map.entry("uq_price_calc_one_active", conflict("PRICE_CALCULATION_ALREADY_ACTIVE",
                    "An active price calculation already exists for this case")),
            Map.entry("uq_command_log_key", conflict("IDEMPOTENCY_KEY_REUSED",
                    "This Idempotency-Key was already used")),
            // --- Reference/admin uniqueness (Phase 4) ---
            Map.entry("uq_app_user_email", conflict("EMAIL_ALREADY_IN_USE", "This email is already registered")),
            Map.entry("uq_department_code", conflict("CODE_ALREADY_IN_USE", "This department code is already in use")),
            Map.entry("uq_position_code", conflict("CODE_ALREADY_IN_USE", "This position code is already in use")),
            Map.entry("uq_service_code", conflict("CODE_ALREADY_IN_USE", "This service code is already in use")),
            Map.entry("uq_work_type_code", conflict("CODE_ALREADY_IN_USE", "This work type code is already in use")),
            Map.entry("uq_external_stage_code",
                    conflict("CODE_ALREADY_IN_USE", "This external stage code is already in use")),
            Map.entry("uq_applicant_pinfl", conflict("PINFL_ALREADY_IN_USE", "This PINFL is already registered")),
            Map.entry("uq_applicant_tin", conflict("TIN_ALREADY_IN_USE", "This TIN is already registered")),
            Map.entry("uq_contract_number",
                    conflict("CONTRACT_NUMBER_ALREADY_IN_USE", "This contract number is already in use")));

    private static Mapping conflict(String code, String message) {
        return new Mapping(HttpStatus.CONFLICT, code, message);
    }

    public Optional<Mapping> translate(DataIntegrityViolationException ex) {
        return findConstraintName(ex).map(name -> MAPPINGS.get(name.toLowerCase()));
    }

    private Optional<String> findConstraintName(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof org.hibernate.exception.ConstraintViolationException hibernateEx
                    && hibernateEx.getConstraintName() != null) {
                return Optional.of(hibernateEx.getConstraintName());
            }
            current = current.getCause();
        }
        return Optional.empty();
    }
}
