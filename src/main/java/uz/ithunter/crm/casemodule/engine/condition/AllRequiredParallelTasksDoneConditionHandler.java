package uz.ithunter.crm.casemodule.engine.condition;

import java.util.List;
import org.springframework.stereotype.Component;
import uz.ithunter.crm.casemodule.CaseStage;
import uz.ithunter.crm.casemodule.CaseStageStatus;
import uz.ithunter.crm.workflow.ConditionType;
import uz.ithunter.crm.workflow.WorkflowTransition;

/**
 * The parallel gate of spec 7.14 - the single most important condition in the system, and the one an
 * interviewer will ask about.
 *
 * <p>{@code condition_value} names the parallel group. The handler asks the lookup for the
 * <b>required</b> siblings of that group and fires only when every one of them is COMPLETED.
 * Consequences, each with a test:
 * <ul>
 *   <li>one incomplete required sibling blocks the dependent stage (W-05);</li>
 *   <li>an incomplete OPTIONAL sibling does not, because {@code required = false} rows never enter
 *       the list (U-10, W-06) - the demo route's AUDIT stage is exactly that case;</li>
 *   <li>an empty group returns {@code false}, not {@code true}. A vacuous "all of nothing is done"
 *       would open a gate that nothing guards; a misconfigured group should stall visibly instead.
 *       {@code WorkflowPublishValidator} already rejects a parallel group with no required member at
 *       publish time, so this can only be reached by a group name that matches nothing.</li>
 * </ul>
 *
 * <p>SKIPPED is not COMPLETED here. A stage skipped by a route decision has no result to gate on, and
 * quietly treating it as done is how a branch gets bypassed without anyone noticing; spec 7.14 says
 * required work must be finished.
 */
@Component
public class AllRequiredParallelTasksDoneConditionHandler implements ConditionHandler {

    @Override
    public ConditionType supportedType() {
        return ConditionType.ALL_REQUIRED_PARALLEL_TASKS_DONE;
    }

    @Override
    public boolean matches(WorkflowTransition transition, TransitionContext context) {
        String group = transition.getConditionValue();
        if (group == null || group.isBlank()) {
            return false;
        }
        List<CaseStage> siblings = context.siblingStageLookup().requiredSiblings(group.trim());
        if (siblings == null || siblings.isEmpty()) {
            return false;
        }
        return siblings.stream().allMatch(sibling -> sibling.getStatus() == CaseStageStatus.COMPLETED);
    }
}
