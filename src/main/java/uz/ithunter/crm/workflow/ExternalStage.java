package uz.ithunter.crm.workflow;

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
 * The applicant-facing stage label (FINAL_DOMAIN_MODEL.md 3.2, spec 5.11, 15.6, 15.7). Maps to
 * {@code external_stage} in V3.
 *
 * <p>Several internal workflow stages may collapse into ONE external stage: the applicant sees
 * "Документы проверяются", not the four internal steps behind it. {@code sequence} orders the
 * public tracking view and is what {@code GET /cases/{id}/tracking} sorts by from Phase 7 onwards.
 */
@Entity
@Table(name = "external_stage")
@Getter
@Setter
@NoArgsConstructor
public class ExternalStage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "code", nullable = false, unique = true, length = 60)
    private String code;

    @Column(name = "name_for_applicant", nullable = false, length = 255)
    private String nameForApplicant;

    // "sequence" is a non-reserved keyword in PostgreSQL and is usable unquoted as a column name,
    // exactly as V3 declares it - no @Column quoting needed.
    @Column(name = "sequence", nullable = false)
    private int sequence;

    @Column(name = "active", nullable = false)
    private boolean active = true;
}
