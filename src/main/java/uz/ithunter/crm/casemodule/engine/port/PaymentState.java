package uz.ithunter.crm.casemodule.engine.port;

/**
 * Everything the engine needs to know about money, and nothing more (spec 12.8).
 *
 * <p>Two booleans rather than a {@code PaymentStatus} enum on purpose: the enum belongs to the
 * finance module that Phase 8 owns, and the engine has no business knowing the difference between
 * {@code UNPAID} and {@code AWAITING_CONFIRMATION}. It only ever asks "may execution start?".
 */
public record PaymentState(boolean fullyPaid, boolean partiallyPaid) {

    public static PaymentState unpaid() {
        return new PaymentState(false, false);
    }
}
