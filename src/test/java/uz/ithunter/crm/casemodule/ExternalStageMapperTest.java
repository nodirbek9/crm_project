package uz.ithunter.crm.casemodule;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import uz.ithunter.crm.casemodule.engine.ExternalStageMapper;
import uz.ithunter.crm.workflow.ExternalStage;
import uz.ithunter.crm.workflow.StageType;
import uz.ithunter.crm.workflow.WorkflowStage;

/**
 * TEST_MATRIX.md U-14: "maps three internal stages onto one external stage". Plain JUnit -
 * {@link ExternalStageMapper} takes plain collections instead of repositories precisely so this can be
 * asserted without Spring and without a database.
 */
class ExternalStageMapperTest {

    private final ExternalStageMapper mapper = new ExternalStageMapper();

    // Codes and sequences as V3__reference_data.sql seeds them (spec 15.7).
    private final ExternalStage documentsUnderReview = external("DOCUMENTS_UNDER_REVIEW", 20);
    private final ExternalStage workInProgress = external("WORK_IN_PROGRESS", 50);
    private final ExternalStage resultIssued = external("RESULT_ISSUED", 100);

    private ExternalStage external(String code, int sequence) {
        ExternalStage stage = new ExternalStage();
        stage.setId(UUID.randomUUID());
        stage.setCode(code);
        stage.setNameForApplicant(code + " for applicant");
        stage.setSequence(sequence);
        stage.setActive(true);
        return stage;
    }

    private WorkflowStage internal(String code, StageType type, int sequence, ExternalStage external) {
        WorkflowStage stage = new WorkflowStage();
        stage.setId(UUID.randomUUID());
        stage.setCode(code);
        stage.setName(code + " stage");
        stage.setStageType(type);
        stage.setSequence(sequence);
        stage.setExternalStageId(external.getId());
        stage.setInternalStatusLabel(code + " in progress");
        stage.setRequired(true);
        return stage;
    }

    /**
     * U-14: three internal stages, one external label - the applicant learns nothing about routing.
     * EXECUTION, ENDORSEMENT and FINAL_REVIEW are three distinct internal steps with three distinct
     * responsible departments, and all three read "Выполняются работы" from outside.
     */
    @Test
    void threeInternalStagesCollapseIntoOneExternalStage() {
        List<WorkflowStage> internals = List.of(
                internal("EXECUTION", StageType.EXECUTION, 30, workInProgress),
                internal("ENDORSEMENT", StageType.ENDORSEMENT, 40, workInProgress),
                internal("FINAL_REVIEW", StageType.FINAL_REVIEW, 50, workInProgress));

        assertThat(mapper.distinctExternalStageCount(internals)).isEqualTo(1);
        assertThat(mapper.resolveCurrent(internals,
                        List.of(documentsUnderReview, workInProgress, resultIssued)))
                .get()
                .extracting(ExternalStage::getCode)
                .isEqualTo("WORK_IN_PROGRESS");
    }

    /**
     * A parallel group spanning two external stages resolves to the LOWEST sequence: the applicant is
     * never told the work has progressed past its slowest open branch.
     */
    @Test
    void aParallelGroupResolvesToTheEarliestExternalStage() {
        List<WorkflowStage> internals = List.of(
                internal("PRIMARY_CHECK", StageType.PRIMARY_CHECK, 10, documentsUnderReview),
                internal("EXECUTION", StageType.EXECUTION, 30, workInProgress));

        assertThat(mapper.distinctExternalStageCount(internals)).isEqualTo(2);
        assertThat(mapper.resolveCurrent(internals,
                        List.of(documentsUnderReview, workInProgress, resultIssued)))
                .get()
                .extracting(ExternalStage::getCode)
                .isEqualTo("DOCUMENTS_UNDER_REVIEW");
    }

    /** Finished cases have no active stage, so the tracking view falls back to the furthest one reached. */
    @Test
    void resolveFurthestTakesTheHighestSequence() {
        List<WorkflowStage> internals = List.of(
                internal("PRIMARY_CHECK", StageType.PRIMARY_CHECK, 10, documentsUnderReview),
                internal("COMPLETION", StageType.COMPLETION, 90, resultIssued));

        assertThat(mapper.resolveFurthest(internals,
                        List.of(documentsUnderReview, workInProgress, resultIssued)))
                .get()
                .extracting(ExternalStage::getCode)
                .isEqualTo("RESULT_ISSUED");
    }

    @Test
    void emptyAndNullInputsProduceNoExternalStage() {
        assertThat(mapper.resolveCurrent(List.of(), List.of(workInProgress))).isEmpty();
        assertThat(mapper.resolveCurrent(null, List.of(workInProgress))).isEmpty();
        assertThat(mapper.resolveFurthest(List.of(), List.of(workInProgress))).isEmpty();
        assertThat(mapper.distinctExternalStageCount(null)).isZero();
    }
}
