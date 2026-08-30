package uz.ithunter.crm.finance;

import java.util.UUID;
import org.springframework.stereotype.Component;
import uz.ithunter.crm.casemodule.engine.port.PaymentState;
import uz.ithunter.crm.casemodule.engine.port.PaymentStateProvider;

/**
 * The Phase 8 implementation of the seam Phase 7 shipped as an interface only
 * (see {@link PaymentStateProvider}'s javadoc). Once this bean exists, Spring wires it into
 * {@code PaymentStateSatisfiedConditionHandler}'s {@code ObjectProvider} automatically - no change
 * needed there.
 */
@Component
public class FinancePaymentStateProvider implements PaymentStateProvider {

    private final PaymentRepository paymentRepository;

    public FinancePaymentStateProvider(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    @Override
    public PaymentState forCase(UUID caseId) {
        return paymentRepository.findByCaseId(caseId)
                .map(payment -> new PaymentState(
                        payment.getStatus() == PaymentStatus.PAID,
                        payment.getStatus() == PaymentStatus.PARTIALLY_PAID))
                .orElseGet(PaymentState::unpaid);
    }
}
