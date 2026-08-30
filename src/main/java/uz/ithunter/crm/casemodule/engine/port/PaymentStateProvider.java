package uz.ithunter.crm.casemodule.engine.port;

import java.util.UUID;

/**
 * The seam between the engine's {@code PAYMENT_STATE_SATISFIED} condition and the finance block.
 *
 * <p>FINAL_IMPLEMENTATION_ORDER.md gives {@code Payment} to Phase 8, so Phase 7 ships this interface
 * with no implementation. The condition handler injects it as an {@code ObjectProvider} and treats
 * "no provider" as {@link PaymentState#unpaid()} - a payment gate with nothing behind it must block,
 * never wave the case through. ASSUMPTIONS.md A25 records that.
 */
public interface PaymentStateProvider {

    PaymentState forCase(UUID caseId);
}
