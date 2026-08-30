package uz.ithunter.crm.workflow;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Maps to {@code workflow_stage} in V4 (spec 5.3, FINAL_DOMAIN_MODEL.md 3.3). */
@Entity
@Table(name = "workflow_stage")
@Getter
@Setter
@NoArgsConstructor
public class WorkflowStage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "workflow_id", nullable = false)
    private UUID workflowId;

    @Column(name = "code", nullable = false, length = 60)
    private String code;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "stage_type", nullable = false, length = 40)
    private StageType stageType;

    @Column(name = "sequence", nullable = false)
    private int sequence;

    @Column(name = "parallel_group", length = 60)
    private String parallelGroup;

    @Column(name = "required", nullable = false)
    private boolean required = true;

    @Column(name = "external_stage_id", nullable = false)
    private UUID externalStageId;

    @Column(name = "internal_status_label", nullable = false, length = 120)
    private String internalStatusLabel;

    @Column(name = "responsible_role_code", length = 40)
    private String responsibleRoleCode;

    @Column(name = "responsible_department_id")
    private UUID responsibleDepartmentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "assignment_mode", nullable = false, length = 30)
    private AssignmentMode assignmentMode = AssignmentMode.DEPARTMENT_HEAD_ASSIGNS;

    @Column(name = "deadline_days")
    private Integer deadlineDays;

    @Column(name = "expedited_deadline_days")
    private Integer expeditedDeadlineDays;

    @Column(name = "work_type_id")
    private UUID workTypeId;

    @Column(name = "produces_document_type", length = 60)
    private String producesDocumentType;

    @Column(name = "requires_result", nullable = false)
    private boolean requiresResult = true;

    @Column(name = "revision_allowed", nullable = false)
    private boolean revisionAllowed = true;

    @Column(name = "approval_required", nullable = false)
    private boolean approvalRequired;

    @Enumerated(EnumType.STRING)
    @Column(name = "approval_mode", length = 20)
    private ApprovalMode approvalMode;
}
