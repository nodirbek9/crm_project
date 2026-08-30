package uz.ithunter.crm.casemodule.engine;

import org.springframework.stereotype.Component;
import uz.ithunter.crm.casemodule.CaseStatus;
import uz.ithunter.crm.workflow.StageType;

/**
 * Maps the {@link StageType} being activated onto the case {@link CaseStatus} (spec 4.2,
 * WORKFLOW_ENGINE_DESIGN.md 4's {@code lifecycleFor}).
 *
 * <p>This mapping is why {@code CaseStatus} may stay a lifecycle rather than degenerating into a
 * duplicate of "which stage am I on" (PLAN_REVIEW M1): several stages can share one status, and the
 * precise position is always {@code case_stage} plus {@code current_stage_id}.
 *
 * <p>Two stage types map to {@code null}, meaning "leave the status alone", and that is deliberate
 * (ASSUMPTIONS.md A24):
 * <ul>
 *   <li>{@code ENDORSEMENT} - {@code ck_case_status} has no ENDORSEMENT value. Endorsement runs on a
 *       document version, not on the case, and forcing the case to {@code ON_SIGNING} before anyone
 *       has signed would misreport it in every list.</li>
 *   <li>{@code NON_APPLICABILITY_OPINION} - the status that matters there was already set by the
 *       primary-check decision that routed the case here.</li>
 * </ul>
 */
@Component
public class CaseLifecycleResolver {

    /** {@code null} = keep the current status. */
    public CaseStatus lifecycleFor(StageType stageType) {
        return switch (stageType) {
            case PRIMARY_CHECK -> CaseStatus.PRIMARY_CHECK;
            case ROUTING -> CaseStatus.PRIMARY_CHECK_DONE;
            case ACCOUNTING -> CaseStatus.IN_ACCOUNTING;
            case PAYMENT_CONTROL -> CaseStatus.WAITING_PAYMENT;
            case EXECUTION -> CaseStatus.IN_EXECUTION;
            case FINAL_REVIEW -> CaseStatus.FINAL_REVIEW;
            case SIGNING -> CaseStatus.ON_SIGNING;
            case COMPLETION -> CaseStatus.COMPLETED;
            case ENDORSEMENT, NON_APPLICABILITY_OPINION -> null;
        };
    }
}
