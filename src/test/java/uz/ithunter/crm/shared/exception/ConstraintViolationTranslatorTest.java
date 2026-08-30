package uz.ithunter.crm.shared.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

/**
 * Phase 12 DoD: "DataIntegrityViolationException -> business error code translation by constraint
 * name." Pure unit test - no Spring context needed, {@link ConstraintViolationTranslator} has no
 * dependencies.
 */
class ConstraintViolationTranslatorTest {

    private final ConstraintViolationTranslator translator = new ConstraintViolationTranslator();

    private DataIntegrityViolationException wrapping(String constraintName) {
        ConstraintViolationException hibernateEx = new ConstraintViolationException(
                "duplicate key", new SQLException("duplicate key value violates unique constraint"),
                constraintName);
        return new DataIntegrityViolationException("wrapped", hibernateEx);
    }

    @Test
    void mapsAKnownConstraintNameToItsBusinessCode() {
        var mapping = translator.translate(wrapping("uq_case_application")).orElseThrow();
        assertThat(mapping.status()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(mapping.code()).isEqualTo("ALREADY_REGISTERED");
    }

    @Test
    void mapsThePaymentConfirmationRaceConstraint() {
        var mapping = translator.translate(wrapping("uq_payment_conf_external")).orElseThrow();
        assertThat(mapping.code()).isEqualTo("DUPLICATE_PAYMENT_CONFIRMATION");
    }

    @Test
    void mapsTheParallelStageActivationRaceConstraint() {
        var mapping = translator.translate(wrapping("uq_case_stage")).orElseThrow();
        assertThat(mapping.code()).isEqualTo("STAGE_ALREADY_ACTIVATED");
    }

    @Test
    void anUnmappedConstraintNameYieldsEmpty() {
        assertThat(translator.translate(wrapping("ck_case_completed"))).isEmpty();
    }

    @Test
    void noConstraintNameAtAllYieldsEmpty() {
        DataIntegrityViolationException plain = new DataIntegrityViolationException("no cause here");
        assertThat(translator.translate(plain)).isEmpty();
    }
}
