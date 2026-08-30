package uz.ithunter.crm.casemodule.engine;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;
import uz.ithunter.crm.casemodule.engine.condition.ConditionHandler;
import uz.ithunter.crm.casemodule.engine.condition.TransitionContext;
import uz.ithunter.crm.workflow.ConditionType;
import uz.ithunter.crm.workflow.WorkflowTransition;

/**
 * Decides whether one {@code workflow_transition} may be traversed right now
 * (WORKFLOW_ENGINE_DESIGN.md 6).
 *
 * <p>There is no {@code switch} on {@link ConditionType} in this class and no {@code if} on a service
 * or route code. Spring injects every {@link ConditionHandler} and they are indexed by the type they
 * declare, so a new condition type is a new class plus a new CHECK-constraint value - never an edit
 * here. That is the whole point of "the route is data".
 *
 * <p>The completeness check lives in {@link #afterPropertiesSet()} rather than in the constructor on
 * purpose: at startup a missing handler must kill the application, but the unit tests
 * (U-10 … U-12) build an evaluator with only the handler under test and no Spring context, which the
 * constructor must therefore allow. A missing handler discovered at evaluation time still fails
 * loudly rather than silently reporting "condition not met".
 */
@Component
public class TransitionEvaluator implements InitializingBean {

    private final Map<ConditionType, ConditionHandler> handlers = new EnumMap<>(ConditionType.class);

    public TransitionEvaluator(List<ConditionHandler> conditionHandlers) {
        for (ConditionHandler handler : conditionHandlers) {
            ConditionHandler previous = handlers.put(handler.supportedType(), handler);
            if (previous != null) {
                throw new IllegalStateException("two ConditionHandlers claim " + handler.supportedType()
                        + ": " + previous.getClass().getName() + " and " + handler.getClass().getName());
            }
        }
    }

    @Override
    public void afterPropertiesSet() {
        for (ConditionType type : ConditionType.values()) {
            if (!handlers.containsKey(type)) {
                throw new IllegalStateException("no ConditionHandler registered for " + type
                        + " - every ck_transition_condition value needs exactly one");
            }
        }
    }

    public boolean matches(WorkflowTransition transition, TransitionContext context) {
        ConditionHandler handler = handlers.get(transition.getConditionType());
        if (handler == null) {
            throw new IllegalStateException("no ConditionHandler registered for "
                    + transition.getConditionType());
        }
        return handler.matches(transition, context);
    }
}
