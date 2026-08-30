package uz.ithunter.crm.casemodule.engine.activator;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import uz.ithunter.crm.audit.AuditAction;
import uz.ithunter.crm.audit.AuditEvent;
import uz.ithunter.crm.audit.AuditWriter;
import uz.ithunter.crm.casemodule.CaseStageStatus;
import uz.ithunter.crm.casemodule.CaseStatus;
import uz.ithunter.crm.casemodule.ElectronicCase;
import uz.ithunter.crm.workflow.StageType;

/**
 * Closes the case (WORKFLOW_ENGINE_DESIGN.md 7: COMPLETION → {@code status = COMPLETED},
 * {@code completed_at = now}, audit {@code CASE_COMPLETED}). The only activator that is fully
 * implemented in Phase 7, and the one test W-01 ends on.
 *
 * <p>It also marks its own {@code case_stage} row COMPLETED, which the pseudocode does not spell out.
 * A COMPLETION stage carries no work and nobody will ever "finish" it, so leaving it ACTIVE would make
 * every finished case report an open stage forever and would put closed cases into any list filtered
 * by active {@code stageCode}. No {@code STAGE_COMPLETED} audit row is written for it: nothing was
 * performed, and {@code CASE_COMPLETED} already records the fact that matters.
 *
 * <p>{@code ck_case_completed} requires {@code completed_at} to be set whenever the status is
 * COMPLETED, so the two assignments below cannot be separated.
 */
@Component
public class CompletionStageActivator implements StageActivator {

    private final AuditWriter auditWriter;

    public CompletionStageActivator(AuditWriter auditWriter) {
        this.auditWriter = auditWriter;
    }

    @Override
    public StageType supportedType() {
        return StageType.COMPLETION;
    }

    @Override
    public void onActivate(StageActivationContext context) {
        ElectronicCase electronicCase = context.electronicCase();
        if (electronicCase.getStatus() == CaseStatus.COMPLETED && electronicCase.getCompletedAt() != null) {
            return; // re-entry after a replay: nothing to change, nothing to audit twice
        }
        Map<String, Object> oldValue = new HashMap<>();
        oldValue.put("status", electronicCase.getStatus().name());

        Instant now = Instant.now();
        electronicCase.setStatus(CaseStatus.COMPLETED);
        electronicCase.setCompletedAt(now);
        context.stage().setStatus(CaseStageStatus.COMPLETED);
        context.stage().setCompletedAt(now);

        Map<String, Object> newValue = new HashMap<>();
        newValue.put("status", CaseStatus.COMPLETED.name());
        newValue.put("completedAt", now.toString());

        auditWriter.write(AuditEvent.forCase(AuditAction.CASE_COMPLETED, "ElectronicCase",
                electronicCase.getId(), electronicCase.getId(), oldValue, newValue, null));
    }
}
