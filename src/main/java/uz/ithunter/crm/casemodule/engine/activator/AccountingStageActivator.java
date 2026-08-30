package uz.ithunter.crm.casemodule.engine.activator;

import org.springframework.stereotype.Component;
import uz.ithunter.crm.workflow.StageType;

/**
 * Does nothing, deliberately (WORKFLOW_ENGINE_DESIGN.md 7: ACCOUNTING → "nothing automatic").
 *
 * <p>The case simply becomes visible to accounting as {@code IN_ACCOUNTING} and waits for a person:
 * spec 1.9 and 4.3 require the processing mode to be chosen by an accountant, and auto-creating a
 * price calculation before that choice would produce a number nobody asked for and immediately have
 * to supersede it (spec 12.3).
 *
 * <p>Written as an explicit class rather than left absent so that a reader of the registry sees the
 * difference between "no side effect by design" and "not implemented yet".
 */
@Component
public class AccountingStageActivator implements StageActivator {

    @Override
    public StageType supportedType() {
        return StageType.ACCOUNTING;
    }

    @Override
    public void onActivate(StageActivationContext context) {
        // Intentionally empty - see the class javadoc.
    }
}
