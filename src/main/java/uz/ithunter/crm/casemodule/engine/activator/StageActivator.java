package uz.ithunter.crm.casemodule.engine.activator;

import uz.ithunter.crm.workflow.StageType;

/**
 * The side effect of entering a stage of a given {@link StageType}
 * (WORKFLOW_ENGINE_DESIGN.md 7): create a task, open an endorsement round, start the payment waiting
 * clock, close the case.
 *
 * <p>One implementation per stage type, resolved through {@link StageActivatorRegistry}. This is the
 * <b>only</b> place in the engine where behaviour is allowed to depend on the kind of stage - and it
 * depends on the type, never on the stage's code or on the service, so a new route reuses these
 * activators unchanged.
 *
 * <p>Implementations run inside the caller's transaction and must be safe to re-enter: a stage can be
 * re-activated after a return to revision (spec 7.13), and {@code activation_count} exists precisely
 * to make that visible.
 */
public interface StageActivator {

    StageType supportedType();

    void onActivate(StageActivationContext context);
}
