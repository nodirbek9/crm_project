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
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Maps to {@code contract} in V6 (FINAL_DOMAIN_MODEL.md 7.3, spec 12.5, 12.10, 1.13).
 *
 * <p>{@code calculatedAmount} is set once, at price confirmation, and never overwritten -
 * spec 12.4's «сохраняет первоначальный расчет». {@code actualAmount} may diverge from it, but
 * only together with {@code amountChangedById}/{@code amountChangedAt}: {@code ck_contract_change_tracked}
 * rejects any other combination, so "who changed the price and when" can never be silently lost.
 *
 * <p>{@code ck_contract_sent} requires {@code contractNumber}/{@code contractDate}/{@code sentChannel}/
 * {@code sentAt} all together whenever {@code sent = true} - `recordContract`/{@link AccountingService}
 * must set all four in the same write, never one at a time.
 */
@Entity
@Table(name = "contract")
@Getter
@Setter
@NoArgsConstructor
public class Contract {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "case_id", nullable = false, unique = true)
    private UUID caseId;

    @Column(name = "contract_number", length = 60)
    private String contractNumber;

    @Column(name = "contract_date")
    private LocalDate contractDate;

    @Column(name = "calculated_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal calculatedAmount;

    @Column(name = "actual_amount", precision = 18, scale = 2)
    private BigDecimal actualAmount;

    @Column(name = "amount_changed_by_id")
    private UUID amountChangedById;

    @Column(name = "amount_changed_at")
    private Instant amountChangedAt;

    @Column(name = "amount_change_reason", length = 1000)
    private String amountChangeReason;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "UZS";

    @Column(name = "sent", nullable = false)
    private boolean sent;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "sent_channel", length = 20)
    private ContractSentChannel sentChannel;

    /** spec 12.10: reference only, CRM never issues the invoice itself. */
    @Column(name = "invoice_reference", length = 120)
    private String invoiceReference;

    @Column(name = "invoice_date")
    private LocalDate invoiceDate;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;
}
