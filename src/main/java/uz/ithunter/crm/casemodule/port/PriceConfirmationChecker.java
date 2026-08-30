package uz.ithunter.crm.casemodule.port;

import java.util.UUID;

/**
 * The seam between {@link uz.ithunter.crm.casemodule.CaseService}'s item-lock check and Phase 8's
 * {@code PriceCalculation} table (ASSUMPTIONS.md A33). A port rather than a direct dependency on
 * the {@code finance} package, for the same reason Phase 7's own
 * {@code casemodule.engine.port.PaymentStateProvider} exists: {@code casemodule} must not depend on
 * a later phase's module, even though that phase depends on {@code casemodule}.
 */
public interface PriceConfirmationChecker {

    /** True once a {@code CONFIRMED} price calculation exists for the case - price is locked in. */
    boolean isPriceConfirmed(UUID caseId);
}
