package uz.ithunter.crm.casemodule.engine.condition;

import uz.ithunter.crm.casemodule.ElectronicCase;
import uz.ithunter.crm.workflow.Workflow;

/**
 * Everything a {@link ConditionHandler} is allowed to look at. Passing one immutable context instead
 * of letting handlers inject repositories is what keeps them unit-testable and keeps the "route is
 * data" rule enforceable by reading the code.
 *
 * @param electronicCase the case being advanced
 * @param workflow the PINNED workflow version the case runs on (spec 5.12) - never the currently
 *        ACTIVE version, or publishing v2 would change how a running case evaluates its conditions
 * @param siblingStageLookup see {@link SiblingStageLookup}
 */
public record TransitionContext(
        ElectronicCase electronicCase,
        Workflow workflow,
        SiblingStageLookup siblingStageLookup) {
}
