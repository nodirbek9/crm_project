package uz.ithunter.crm.finance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Immutable;

/**
 * Maps to {@code payment_confirmation} in V6 - the append-only ledger of accounting confirmations
 * (spec 1.14, 12.7: "CRM never processes money", accounting only records confirmations). A BEFORE
 * UPDATE OR DELETE trigger ({@code tr_payment_confirmation_immutable}, {@code forbid_mutation()})
 * rejects mutation at the DB level; {@code @Immutable} mirrors the same guarantee on the Hibernate
 * side, matching {@code audit/AuditLog}'s precedent.
 *
 * <p>{@code uq_payment_conf_external UNIQUE(payment_id, external_reference)} is an idempotency
 * guard against a double-clicked confirmation - {@code externalReference} is nullable, so
 * confirmations without one never collide with each other, only a literal repeat of the same
 * reference for the same payment does.
 */
@Entity
@Table(name = "payment_confirmation")
@Immutable
@Getter
@Setter
@NoArgsConstructor
public class PaymentConfirmation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "payment_id", nullable = false)
    private UUID paymentId;

    @Column(name = "amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(name = "confirmed_by_id", nullable = false)
    private UUID confirmedById;

    @Column(name = "confirmed_at", nullable = false)
    private Instant confirmedAt;

    @Column(name = "note", length = 1000)
    private String note;

    @Column(name = "external_reference", length = 120)
    private String externalReference;
}
