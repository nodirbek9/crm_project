package uz.ithunter.crm.finance;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uz.ithunter.crm.audit.AuditAction;
import uz.ithunter.crm.audit.AuditEvent;
import uz.ithunter.crm.audit.AuditWriter;
import uz.ithunter.crm.casemodule.ElectronicCase;
import uz.ithunter.crm.casemodule.ElectronicCaseRepository;
import uz.ithunter.crm.finance.port.OverduePaymentTaskCreator;

/**
 * The waiting-period sweep of spec 12.9 (ASSUMPTIONS.md A5): flags a payment {@code overdue} once
 * its {@code dueAt} has passed, and asks Phase 9's not-yet-existing task queue to raise a manager
 * decision (ASSUMPTIONS.md A35). It NEVER changes {@link PaymentStatus} and never touches the
 * case's route - spec 12.9 is explicit that only an authorized manager may reject on this, via
 * {@code AccountingService#setPaymentStatus}, never the scheduler itself.
 */
@Component
public class PaymentWaitingScheduler {

    private final PaymentRepository paymentRepository;
    private final ElectronicCaseRepository electronicCaseRepository;
    private final AuditWriter auditWriter;
    private final ObjectProvider<OverduePaymentTaskCreator> taskCreator;

    public PaymentWaitingScheduler(PaymentRepository paymentRepository,
            ElectronicCaseRepository electronicCaseRepository, AuditWriter auditWriter,
            ObjectProvider<OverduePaymentTaskCreator> taskCreator) {
        this.paymentRepository = paymentRepository;
        this.electronicCaseRepository = electronicCaseRepository;
        this.auditWriter = auditWriter;
        this.taskCreator = taskCreator;
    }

    @Scheduled(fixedDelayString = "PT15M", initialDelayString = "PT1M")
    @Transactional
    public void flagOverduePayments() {
        sweep(Instant.now());
    }

    /** Package-visible so a test can drive the sweep at a chosen instant instead of waiting on the clock. */
    @Transactional
    public int sweep(Instant now) {
        List<Payment> overdue = paymentRepository.findOverdueUnflagged(now);
        for (Payment payment : overdue) {
            payment.setOverdue(true);
            paymentRepository.save(payment);

            ElectronicCase electronicCase = electronicCaseRepository.findById(payment.getCaseId()).orElse(null);
            if (electronicCase != null) {
                electronicCase.setPaymentOverdue(true);
                electronicCaseRepository.save(electronicCase);
            }

            auditWriter.write(AuditEvent.forCase(AuditAction.PAYMENT_OVERDUE, "Payment", payment.getId(),
                    payment.getCaseId(), null,
                    Map.of("dueAt", String.valueOf(payment.getDueAt()), "status", payment.getStatus().name()),
                    null));

            OverduePaymentTaskCreator creator = taskCreator.getIfAvailable();
            if (creator != null) {
                creator.createDecisionTask(payment.getCaseId(), payment.getId());
            }
        }
        return overdue.size();
    }
}
