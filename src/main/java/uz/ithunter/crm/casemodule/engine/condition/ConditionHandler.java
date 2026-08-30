package uz.ithunter.crm.casemodule.engine.condition;

import uz.ithunter.crm.workflow.ConditionType;
import uz.ithunter.crm.workflow.WorkflowTransition;

/**
 * One handler per {@link ConditionType} (WORKFLOW_ENGINE_DESIGN.md 6). Registered by Spring into a
 * map keyed by {@link #supportedType()}, so {@code TransitionEvaluator} contains no {@code switch}
 * and adding a condition type means adding a class, never editing the evaluator.
 */
public interface ConditionHandler {

    ConditionType supportedType();

    boolean matches(WorkflowTransition transition, TransitionContext context);
}
