package uz.ithunter.crm.casemodule.engine.condition;

import org.springframework.stereotype.Component;
import uz.ithunter.crm.casemodule.PrimaryCheckDecision;
import uz.ithunter.crm.workflow.ConditionType;
import uz.ithunter.crm.workflow.WorkflowTransition;

/**
 * Matches the recorded {@link PrimaryCheckDecision} (spec 4.7, PLAN_REVIEW H4). Category and
 * decision are two independent values, so this handler and
 * {@link PrimaryCheckCategoryInConditionHandler} are two independent conditions - a route may branch
 * on either or on both, which is exactly what test U-04 protects.
 */
@Component
public class PrimaryCheckDecisionIsConditionHandler implements ConditionHandler {

    @Override
    public ConditionType supportedType() {
        return ConditionType.PRIMARY_CHECK_DECISION_IS;
    }

    @Override
    public boolean matches(WorkflowTransition transition, TransitionContext context) {
        PrimaryCheckDecision decision = context.electronicCase().getPrimaryCheckDecision();
        if (decision == null || transition.getConditionValue() == null) {
            return false;
        }
        for (String allowed : transition.getConditionValue().split(",")) {
            if (allowed.trim().equalsIgnoreCase(decision.name())) {
                return true;
            }
        }
        return false;
    }
}
