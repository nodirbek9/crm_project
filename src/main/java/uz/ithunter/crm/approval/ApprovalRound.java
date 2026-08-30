package uz.ithunter.crm.approval;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import uz.ithunter.crm.casemodule.ElectronicCase;
import uz.ithunter.crm.document.DocumentVersion;
import uz.ithunter.crm.workflow.ApprovalMode;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "approval_round")
@Getter
@Setter
@NoArgsConstructor
public class ApprovalRound {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_version_id", nullable = false)
    private DocumentVersion documentVersion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "case_id", nullable = false)
    private ElectronicCase electronicCase;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode", nullable = false, length = 20)
    private ApprovalMode mode;

    @Column(name = "round_no", nullable = false)
    private int roundNo = 1;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ApprovalRoundStatus status = ApprovalRoundStatus.IN_PROGRESS;

    @Column(name = "initiated_by_id", nullable = false)
    private UUID initiatedById;

    @Column(name = "initiated_at", nullable = false, updatable = false)
    private Instant initiatedAt = Instant.now();

    @Column(name = "completed_at")
    private Instant completedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;
}
