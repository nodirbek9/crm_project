package uz.ithunter.crm.finance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Maps to {@code payment} in V6 (FINAL_DOMAIN_MODEL.md 7.4, spec 12.7-12.9). CRM never processes
 * money (spec 1.14) - this row only ever records what accounting has confirmed.
 *
 * <p>{@code ck_payment_debt_consistent} requires {@code debtAmount == contractAmount - confirmedAmount}
 * exactly, so every write through {@link AccountingService} recomputes both together - never one
 * without the other.
 */
@Entity
@Table(name = "payment")
@Getter
@Setter
@NoArgsConstructor
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "case_id", nullable = false, unique = true)
    private UUID caseId;

    @Column(name = "contract_id")
    private UUID contractId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PaymentStatus status = PaymentStatus.WAITING_PAYMENT;

    @Column(name = "contract_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal contractAmount;

    @Column(name = "confirmed_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal confirmedAmount = BigDecimal.ZERO;

    @Column(name = "debt_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal debtAmount = BigDecimal.ZERO;

    @Column(name = "waiting_since")
    private Instant waitingSince;

    /** spec 12.9, configurable per route via {@code workflow.payment_waiting_days} (ASSUMPTIONS A5). */
    @Column(name = "due_at")
    private Instant dueAt;

    @Column(name = "overdue", nullable = false)
    private boolean overdue;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;
}
