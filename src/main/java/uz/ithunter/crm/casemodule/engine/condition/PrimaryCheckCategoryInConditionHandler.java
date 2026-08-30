package uz.ithunter.crm.casemodule.engine.condition;

import org.springframework.stereotype.Component;
import uz.ithunter.crm.casemodule.PrimaryCheckCategory;
import uz.ithunter.crm.workflow.ConditionType;
import uz.ithunter.crm.workflow.WorkflowTransition;

/**
 * {@code condition_value} is a comma-separated list of {@link PrimaryCheckCategory} names, e.g.
 * {@code "GREEN,YELLOW"} (spec 4.6). A list rather than one value because routes legitimately treat
 * GREEN and YELLOW alike while sending RED somewhere else, and encoding that as data avoids two
 * near-duplicate transitions.
 *
 * <p>An unset category never matches: the case has not been checked yet, so no category-dependent
 * edge may fire.
 */
@Component
public class PrimaryCheckCategoryInConditionHandler implements ConditionHandler {

    @Override
    public ConditionType supportedType() {
        return ConditionType.PRIMARY_CHECK_CATEGORY_IN;
    }

    @Override
    public boolean matches(WorkflowTransition transition, TransitionContext context) {
        PrimaryCheckCategory category = context.electronicCase().getPrimaryCheckCategory();
        if (category == null || transition.getConditionValue() == null) {
            return false;
        }
        for (String allowed : transition.getConditionValue().split(",")) {
            if (allowed.trim().equalsIgnoreCase(category.name())) {
                return true;
            }
        }
        return false;
    }
}
