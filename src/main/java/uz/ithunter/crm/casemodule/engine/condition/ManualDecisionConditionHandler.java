package uz.ithunter.crm.casemodule.engine.condition;

import org.springframework.stereotype.Component;
import uz.ithunter.crm.workflow.ConditionType;
import uz.ithunter.crm.workflow.WorkflowTransition;

/**
 * Always {@code false} - and that is the implementation, not a stub.
 *
 * <p>{@code MANUAL_DECISION} marks an edge that a human takes deliberately: a route change, a return
 * to the applicant, a manager choosing one of two branches. The engine must never traverse it while
 * cascading, or the "manual" in the name would be a lie. The command that represents the human
 * decision activates the target stage explicitly, through the same idempotent
 * {@code WorkflowEngine.activateStage}.
 *
 * <p>Registering it as a real handler instead of leaving the type unmapped keeps
 * {@code TransitionEvaluator}'s "every condition type has exactly one handler" invariant true, which
 * is what turns a future new enum value into a startup failure rather than a silently dead route.
 */
@Component
public class ManualDecisionConditionHandler implements ConditionHandler {

    @Override
    public ConditionType supportedType() {
        return ConditionType.MANUAL_DECISION;
    }

    @Override
    public boolean matches(WorkflowTransition transition, TransitionContext context) {
        return false;
    }
}
