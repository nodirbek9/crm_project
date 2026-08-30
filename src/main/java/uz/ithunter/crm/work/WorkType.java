package uz.ithunter.crm.work;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One row of the performed-works matrix of spec 8.2 (FINAL_DOMAIN_MODEL.md 2.3). Maps to
 * {@code work_type} in V3.
 *
 * <p>The specification calls its matrix an INITIAL list, which is why this is a configurable table
 * with an admin CRUD surface and not a Java enum: adding a work type must not require a release.
 *
 * <p>{@code serviceScope} and {@code stageKind} are free-form {@code varchar} labels in the schema,
 * not FKs - the matrix groups work types by service family and by the kind of stage that produces
 * them, and those groupings are configuration too.
 *
 * <p>{@code requiresContractAmountBracket} is spec 8.4: green-certification work is counted per
 * contract-amount bracket, everything else is not.
 */
@Entity
@Table(name = "work_type")
@Getter
@Setter
@NoArgsConstructor
public class WorkType {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "code", nullable = false, unique = true, length = 60)
    private String code;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "service_scope", length = 120)
    private String serviceScope;

    @Column(name = "stage_kind", length = 120)
    private String stageKind;

    @Column(name = "requires_contract_amount_bracket", nullable = false)
    private boolean requiresContractAmountBracket = false;

    /** The matrix column "Основание для расчета" - what document justifies counting the work. */
    @Column(name = "basis_document_description", length = 255)
    private String basisDocumentDescription;

    @Column(name = "active", nullable = false)
    private boolean active = true;
}
