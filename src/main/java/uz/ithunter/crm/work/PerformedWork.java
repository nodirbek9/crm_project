package uz.ithunter.crm.work;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import uz.ithunter.crm.shared.domain.ProcessingMode;

/**
 * One recorded unit of work per (case, work type, stage) — spec 8.1–8.5, V9 schema.
 *
 * <p>The unique index {@code uq_performed_work_once} on (case_id, work_type_id,
 * COALESCE(case_stage_id, '00000000-...')) means this row is an UPSERT target: a revision cycle
 * re-uses the existing row (updating supportingDocumentVersionId only) rather than inserting a
 * second one. The {@code tr_performed_work_no_delete} trigger makes this append-only like
 * audit_log: once inserted, only the supporting document column may change.
 */
@Entity
@Table(name = "performed_work")
@Getter
@Setter
@NoArgsConstructor
public class PerformedWork {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "case_id", nullable = false)
    private UUID caseId;

    @Column(name = "work_type_id", nullable = false)
    private UUID workTypeId;

    @Column(name = "case_stage_id")
    private UUID caseStageId;

    @Column(name = "workflow_stage_id")
    private UUID workflowStageId;

    @Column(name = "service_id", nullable = false)
    private UUID serviceId;

    @Column(name = "department_id", nullable = false)
    private UUID departmentId;

    @Column(name = "executor_user_id", nullable = false)
    private UUID executorUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "processing_mode", nullable = false, length = 20)
    private ProcessingMode processingMode;

    @Column(name = "performed_at", nullable = false)
    private Instant performedAt;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt = Instant.now();

    @Column(name = "recorded_by_id")
    private UUID recordedById;

    @Column(name = "supporting_document_version_id")
    private UUID supportingDocumentVersionId;

    @Column(name = "invoice_reference", length = 120)
    private String invoiceReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "contract_amount_bracket", length = 20)
    private ContractAmountBracket contractAmountBracket;

    @Column(name = "countable", nullable = false)
    private boolean countable = true;
}
