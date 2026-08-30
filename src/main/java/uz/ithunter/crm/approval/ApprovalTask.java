package uz.ithunter.crm.approval;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "approval_task")
@Getter
@Setter
@NoArgsConstructor
public class ApprovalTask {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approval_round_id", nullable = false)
    private ApprovalRound approvalRound;

    @Enumerated(EnumType.STRING)
    @Column(name = "participant_kind", nullable = false, length = 20)
    private ParticipantKind participantKind;

    @Column(name = "participant_user_id")
    private UUID participantUserId;

    @Column(name = "participant_department_id")
    private UUID participantDepartmentId;

    @Column(name = "required", nullable = false)
    private boolean required = true;

    @Column(name = "sequence_no", nullable = false)
    private int sequenceNo = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ApprovalTaskStatus status = ApprovalTaskStatus.SENT;

    @Column(name = "comment", length = 2000)
    private String comment;

    @Column(name = "decided_by_id")
    private UUID decidedById;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "due_at")
    private Instant dueAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
