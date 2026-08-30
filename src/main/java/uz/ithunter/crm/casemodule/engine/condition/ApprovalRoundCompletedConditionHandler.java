package uz.ithunter.crm.casemodule.engine.condition;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import uz.ithunter.crm.casemodule.engine.port.ApprovalStateProvider;
import uz.ithunter.crm.workflow.ConditionType;
import uz.ithunter.crm.workflow.WorkflowTransition;

/**
 * Fires when the endorsement round on the stage this edge LEAVES has completed (spec 13.7). The
 * stage is taken from {@code transition.fromStageId} rather than from the case, because
 * {@code current_stage_id} is NULL whenever a parallel group is open and the transition itself
 * already names its own source.
 *
 * <p>{@code ApprovalRound} arrives in Phase 10; with no {@link ApprovalStateProvider} bean the answer
 * is {@code false} and the case waits at the endorsement stage - the correct behaviour for an
 * unimplemented gate (ASSUMPTIONS.md A25).
 */
@Component
public class ApprovalRoundCompletedConditionHandler implements ConditionHandler {

    private final ObjectProvider<ApprovalStateProvider> approvalStateProvider;

    public ApprovalRoundCompletedConditionHandler(ObjectProvider<ApprovalStateProvider> approvalStateProvider) {
        this.approvalStateProvider = approvalStateProvider;
    }

    @Override
    public ConditionType supportedType() {
        return ConditionType.APPROVAL_ROUND_COMPLETED;
    }

    @Override
    public boolean matches(WorkflowTransition transition, TransitionContext context) {
        ApprovalStateProvider provider = approvalStateProvider.getIfAvailable();
        return provider != null
                && provider.isRoundCompleted(context.electronicCase().getId(), transition.getFromStageId());
    }
}
