package uz.ithunter.crm.casemodule.engine.condition;

import java.util.List;
import uz.ithunter.crm.casemodule.CaseStage;

/**
 * How a condition handler reaches the sibling stages of a parallel group.
 *
 * <p>A functional interface instead of a repository dependency, for one concrete reason: in
 * production {@link uz.ithunter.crm.casemodule.engine.WorkflowEngine} passes
 * {@code CaseStageRepository::lockRequiredSiblings}, which issues {@code SELECT ... FOR UPDATE} and
 * serialises two siblings finishing at the same instant (spec 7.14); in test U-10 the same handler
 * receives a lambda over a hand-built list and needs neither Spring nor a database. The locking
 * decision stays in the engine, where the transaction is, and the condition logic stays pure.
 */
@FunctionalInterface
public interface SiblingStageLookup {

    /** The REQUIRED stages of the given parallel group; optional siblings are already excluded. */
    List<CaseStage> requiredSiblings(String parallelGroup);
}
