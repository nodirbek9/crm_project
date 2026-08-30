package uz.ithunter.crm.task;

import org.springframework.stereotype.Component;
import uz.ithunter.crm.casemodule.engine.activator.StageActivationContext;
import uz.ithunter.crm.casemodule.engine.activator.StageActivator;
import uz.ithunter.crm.workflow.StageType;

/**
 * Creates a {@link Task} row when an EXECUTION stage activates (spec 7.3, 7.4). This is the seam
 * {@link uz.ithunter.crm.casemodule.engine.activator.StageActivatorRegistry} documented in its
 * javadoc as "Phase 9 (FINAL_IMPLEMENTATION_ORDER.md names ExecutionStageActivator)".
 *
 * <p>The engine calls {@link #onActivate} exactly once per true activation (idempotency is
 * structural via {@code uq_task_case_stage} — a replayed activation finds an existing ACTIVE stage
 * and returns without re-firing the activator). Therefore {@link TaskFactory#createFor} does not
 * need its own idempotency guard beyond the DB unique constraint.
 *
 * <p>ASSUMPTIONS.md A37: {@code PRIMARY_CHECK} does NOT create a task — Phase 7 already
 * implemented that flow via {@code POST /cases/{id}/primary-check}. {@code EXECUTION} and
 * {@code FINAL_REVIEW} both create tasks through their own activator classes.
 */
@Component
public class ExecutionStageActivator implements StageActivator {

    private final TaskFactory taskFactory;

    public ExecutionStageActivator(TaskFactory taskFactory) {
        this.taskFactory = taskFactory;
    }

    @Override
    public StageType supportedType() {
        return StageType.EXECUTION;
    }

    @Override
    public void onActivate(StageActivationContext context) {
        taskFactory.createFor(context);
    }
}
