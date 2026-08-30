package uz.ithunter.crm.casemodule.engine.condition;

import org.springframework.stereotype.Component;
import uz.ithunter.crm.shared.domain.ProcessingMode;
import uz.ithunter.crm.workflow.ConditionType;
import uz.ithunter.crm.workflow.WorkflowTransition;

/**
 * Branches on TRADITIONAL vs EXPEDITED (spec 1.8, 5.7). An unset mode never matches - accounting has
 * not chosen yet (spec 1.9), and the applicant may never choose (spec 15.4), so guessing here would
 * silently pick a route on the applicant's behalf.
 */
@Component
public class ProcessingModeIsConditionHandler implements ConditionHandler {

    @Override
    public ConditionType supportedType() {
        return ConditionType.PROCESSING_MODE_IS;
    }

    @Override
    public boolean matches(WorkflowTransition transition, TransitionContext context) {
        ProcessingMode mode = context.electronicCase().getProcessingMode();
        return mode != null && transition.getConditionValue() != null
                && mode.name().equalsIgnoreCase(transition.getConditionValue().trim());
    }
}
