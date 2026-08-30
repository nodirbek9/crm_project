package uz.ithunter.crm.finance;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByCaseId(UUID caseId);

    /** {@link PaymentWaitingScheduler}'s sweep (spec 12.9). */
    @Query("""
            select p from Payment p
            where p.status in (uz.ithunter.crm.finance.PaymentStatus.WAITING_PAYMENT,
                                uz.ithunter.crm.finance.PaymentStatus.PARTIALLY_PAID)
              and p.overdue = false
              and p.dueAt is not null
              and p.dueAt < :now
            """)
    List<Payment> findOverdueUnflagged(@Param("now") Instant now);
}
