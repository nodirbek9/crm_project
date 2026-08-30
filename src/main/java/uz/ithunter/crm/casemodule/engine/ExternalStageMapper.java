package uz.ithunter.crm.casemodule.engine;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import uz.ithunter.crm.workflow.ExternalStage;
import uz.ithunter.crm.workflow.WorkflowStage;

/**
 * Collapses the internal stages a case is currently sitting on into the ONE external stage the
 * applicant is shown (spec 5.11, 15.6, PLAN_REVIEW H1).
 *
 * <p>The mapping is N:1 by design: {@code IDENTIFICATION}, {@code EXPERT_REVIEW} and {@code AUDIT}
 * all point at {@code WORK_IN_PROGRESS}, so the applicant sees "работа выполняется" and learns
 * nothing about internal routing, department structure or who is doing what (spec 15.13). Test U-14
 * pins exactly that, and it needs neither Spring nor a database because this class takes plain
 * collections instead of repositories.
 *
 * <p>When several stages are active - a parallel group - the external stage with the LOWEST
 * {@code sequence} wins. Not the highest: telling the applicant the work has progressed further than
 * its slowest open branch would be a promise the system cannot keep.
 */
@Component
public class ExternalStageMapper {

    /**
     * @param activeStages the {@code workflow_stage} configurations of the case's active stages
     * @param externalStages the candidate external stages (any superset is fine)
     */
    public Optional<ExternalStage> resolveCurrent(Collection<WorkflowStage> activeStages,
            Collection<ExternalStage> externalStages) {
        if (activeStages == null || activeStages.isEmpty() || externalStages == null) {
            return Optional.empty();
        }
        Map<UUID, ExternalStage> byId = new HashMap<>();
        for (ExternalStage external : externalStages) {
            byId.put(external.getId(), external);
        }
        return activeStages.stream()
                .map(stage -> byId.get(stage.getExternalStageId()))
                .filter(external -> external != null)
                .min((left, right) -> Integer.compare(left.getSequence(), right.getSequence()));
    }

    /**
     * The FURTHEST external stage the given internal stages reach - highest {@code sequence}.
     *
     * <p>Only for cases that sit on no active stage any more (COMPLETED, REJECTED, RETURNED): with
     * nothing in progress, {@link #resolveCurrent} has nothing to collapse, and showing the applicant
     * a blank stage on a finished case would be worse than showing where it ended. Never use this for
     * a running case - that is the "promise the system cannot keep" that {@code resolveCurrent}
     * avoids by taking the minimum.
     */
    public Optional<ExternalStage> resolveFurthest(Collection<WorkflowStage> stages,
            Collection<ExternalStage> externalStages) {
        if (stages == null || stages.isEmpty() || externalStages == null) {
            return Optional.empty();
        }
        Map<UUID, ExternalStage> byId = new HashMap<>();
        for (ExternalStage external : externalStages) {
            byId.put(external.getId(), external);
        }
        return stages.stream()
                .map(stage -> byId.get(stage.getExternalStageId()))
                .filter(external -> external != null)
                .max((left, right) -> Integer.compare(left.getSequence(), right.getSequence()));
    }

    /**
     * How many DISTINCT external stages the given internal stages collapse into. Exists so that
     * U-14 can assert the collapsing itself ("three internal stages, one external stage") instead of
     * only asserting which one came out on top.
     */
    public int distinctExternalStageCount(Collection<WorkflowStage> stages) {
        if (stages == null) {
            return 0;
        }
        Set<UUID> ids = new HashSet<>();
        for (WorkflowStage stage : stages) {
            if (stage.getExternalStageId() != null) {
                ids.add(stage.getExternalStageId());
            }
        }
        return ids.size();
    }
}
