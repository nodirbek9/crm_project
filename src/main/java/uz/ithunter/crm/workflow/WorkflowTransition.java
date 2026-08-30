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

/**
 * Maps to {@code workflow_transition} in V4 (FINAL_DOMAIN_MODEL.md 3.4). {@code fromStageId == null}
 * marks the entry transition. The route is data - {@link ConditionType} is the only thing that
 * varies, no code branches per route.
 */
@Entity
@Table(name = "workflow_transition")
@Getter
@Setter
@NoArgsConstructor
public class WorkflowTransition {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "workflow_id", nullable = false)
    private UUID workflowId;

    @Column(name = "from_stage_id")
    private UUID fromStageId;

    @Column(name = "to_stage_id", nullable = false)
    private UUID toStageId;

    @Enumerated(EnumType.STRING)
    @Column(name = "condition_type", nullable = false, length = 50)
    private ConditionType conditionType;

    @Column(name = "condition_value", length = 255)
    private String conditionValue;

    @Column(name = "sequence", nullable = false)
    private int sequence;
}
