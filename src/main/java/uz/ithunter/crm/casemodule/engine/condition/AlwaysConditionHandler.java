package uz.ithunter.crm.casemodule.engine.condition;

import org.springframework.stereotype.Component;
import uz.ithunter.crm.workflow.ConditionType;
import uz.ithunter.crm.workflow.WorkflowTransition;

/** An unconditional edge - the backbone of a linear route and of every entry transition. */
@Component
public class AlwaysConditionHandler implements ConditionHandler {

    @Override
    public ConditionType supportedType() {
        return ConditionType.ALWAYS;
    }

    @Override
    public boolean matches(WorkflowTransition transition, TransitionContext context) {
        return true;
    }
}
