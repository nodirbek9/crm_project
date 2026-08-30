package uz.ithunter.crm.casemodule;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * The pricing item composition (spec 12.6, PLAN_REVIEW H2), mapping to {@code case_item} in V5.
 * Without these rows "хранение состава позиций, на основании которых сформирована договорная сумма"
 * is unimplementable, and Phase 8's {@code PriceCalculator} would have nothing to emit one line per.
 *
 * <p>Materialised at registration from {@code application.form_data -> items} - there is deliberately
 * no {@code application_item} table: before registration the composition is part of the submitted
 * form, after registration it belongs to the case (test I-01's sibling assertion).
 *
 * <p>{@code quantity} is {@code numeric(14,3)} in the DB, so {@link BigDecimal} rather than a double:
 * this number ends up multiplied into a contract sum.
 */
@Entity
@Table(name = "case_item")
@Getter
@Setter
@NoArgsConstructor
public class CaseItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "case_id", nullable = false)
    private UUID caseId;

    @Column(name = "line_no", nullable = false)
    private int lineNo;

    @Column(name = "item_name", nullable = false, length = 255)
    private String itemName;

    @Column(name = "item_code", length = 60)
    private String itemCode;

    // ck_case_item_quantity CHECK (quantity > 0)
    @Column(name = "quantity", nullable = false, precision = 14, scale = 3)
    private BigDecimal quantity = BigDecimal.ONE;

    @Column(name = "unit", nullable = false, length = 20)
    private String unit = "PCS";

    @Column(name = "object_address", length = 500)
    private String objectAddress;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "attributes", nullable = false)
    private String attributes = "{}";

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    // DB-trigger managed (tr_case_item_updated -> set_updated_at()); never written by Hibernate.
    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;
}
