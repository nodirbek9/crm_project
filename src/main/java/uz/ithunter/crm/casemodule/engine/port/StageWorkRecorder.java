package uz.ithunter.crm.casemodule.engine.port;

import uz.ithunter.crm.casemodule.CaseStage;
import uz.ithunter.crm.casemodule.ElectronicCase;

/**
 * The {@code PerformedWorkRecorder.recordIfConfigured} hook of WORKFLOW_ENGINE_DESIGN.md 5 (spec
 * 8.1, 8.5). {@code PerformedWork} belongs to Phase 11, which will implement this interface; until
 * then {@code completeStage} finds no provider and records nothing.
 *
 * <p>Named as a port rather than as the concrete {@code PerformedWorkRecorder} so that Phase 11 can
 * keep the class name FINAL_IMPLEMENTATION_ORDER.md gives it without a collision here.
 */
public interface StageWorkRecorder {

    /** Must be idempotent per (case, work type, executor): a revision cycle records one row, not two. */
    void recordIfConfigured(ElectronicCase electronicCase, CaseStage stage);
}
