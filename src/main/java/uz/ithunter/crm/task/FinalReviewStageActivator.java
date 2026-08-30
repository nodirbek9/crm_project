package uz.ithunter.crm.task;

import org.springframework.stereotype.Component;
import uz.ithunter.crm.casemodule.engine.activator.StageActivationContext;
import uz.ithunter.crm.casemodule.engine.activator.StageActivator;
import uz.ithunter.crm.workflow.StageType;

/**
 * Creates a {@link Task} row when a FINAL_REVIEW stage activates (FINAL_DOMAIN_MODEL.md 5.1,
 * StageActivatorRegistry javadoc). Same logic as {@link ExecutionStageActivator} — the two are
 * separate classes because {@code StageActivatorRegistry} maps one class per type and
 * {@code FINAL_REVIEW} is a distinct {@link StageType}.
 *
 * <p>ASSUMPTIONS.md A37: FINAL_REVIEW creates a task because a specialist must produce a final
 * review document before the case can proceed to endorsement/signing.
 */
@Component
public class FinalReviewStageActivator implements StageActivator {

    private final TaskFactory taskFactory;

    public FinalReviewStageActivator(TaskFactory taskFactory) {
        this.taskFactory = taskFactory;
    }

    @Override
    public StageType supportedType() {
        return StageType.FINAL_REVIEW;
    }

    @Override
    public void onActivate(StageActivationContext context) {
        taskFactory.createFor(context);
    }
}
