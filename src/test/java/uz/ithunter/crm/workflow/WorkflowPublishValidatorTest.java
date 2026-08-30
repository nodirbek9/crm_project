package uz.ithunter.crm.workflow;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import uz.ithunter.crm.shared.exception.IllegalStateTransitionException;

/**
 * Plain unit test (no Spring, no DB - TEST_MATRIX.md's "src/test/java/.../unit/" layout) for the
 * seven publish-time rules of WORKFLOW_ENGINE_DESIGN.md 2 / {@link WorkflowPublishValidator}.
 * Deliberately does not go through the REST layer: at least one of these scenarios
 * (approval-required-without-mode) is also rejected by {@code ck_stage_approval_consistent} at
 * write time, so the only way to exercise the *validator's own* rule in isolation is to hand it
 * in-memory objects that never touch the database.
 */
class WorkflowPublishValidatorTest {

    private final WorkflowPublishValidator validator = new WorkflowPublishValidator();
    private final UUID workflowId = UUID.randomUUID();

    private WorkflowStage stage(String code, StageType type, boolean required, String parallelGroup) {
        return stage(code, type, required, parallelGroup, false, null, null, null);
    }

    private WorkflowStage stage(String code, StageType type, boolean required, String parallelGroup,
            boolean approvalRequired, ApprovalMode approvalMode, Integer deadlineDays, Integer expeditedDeadlineDays) {
        WorkflowStage stage = new WorkflowStage();
        stage.setId(UUID.randomUUID());
        stage.setWorkflowId(workflowId);
        stage.setCode(code);
        stage.setName(code);
        stage.setStageType(type);
        stage.setRequired(required);
        stage.setParallelGroup(parallelGroup);
        stage.setExternalStageId(UUID.randomUUID());
        stage.setInternalStatusLabel(code);
        stage.setAssignmentMode(AssignmentMode.DEPARTMENT_HEAD_ASSIGNS);
        stage.setDeadlineDays(deadlineDays);
        stage.setExpeditedDeadlineDays(expeditedDeadlineDays);
        stage.setApprovalRequired(approvalRequired);
        stage.setApprovalMode(approvalMode);
        return stage;
    }

    private WorkflowTransition transition(UUID from, UUID to, ConditionType type) {
        WorkflowTransition transition = new WorkflowTransition();
        transition.setId(UUID.randomUUID());
        transition.setWorkflowId(workflowId);
        transition.setFromStageId(from);
        transition.setToStageId(to);
        transition.setConditionType(type);
        return transition;
    }

    @Test
    void validGraphPasses() {
        WorkflowStage exec = stage("EXEC", StageType.EXECUTION, true, null);
        WorkflowStage completion = stage("DONE", StageType.COMPLETION, true, null);
        List<WorkflowStage> stages = List.of(exec, completion);
        List<WorkflowTransition> transitions = List.of(
                transition(null, exec.getId(), ConditionType.ALWAYS),
                transition(exec.getId(), completion.getId(), ConditionType.ALWAYS));

        assertThatCode(() -> validator.validate(stages, transitions)).doesNotThrowAnyException();
    }

    @Test
    void missingEntryTransitionIsRejected() {
        WorkflowStage exec = stage("EXEC", StageType.EXECUTION, true, null);
        WorkflowStage completion = stage("DONE", StageType.COMPLETION, true, null);
        List<WorkflowStage> stages = List.of(exec, completion);
        List<WorkflowTransition> transitions = List.of(
                transition(exec.getId(), completion.getId(), ConditionType.ALWAYS));

        assertThatThrownBy(() -> validator.validate(stages, transitions))
                .isInstanceOf(IllegalStateTransitionException.class)
                .extracting(ex -> ((IllegalStateTransitionException) ex).getCode())
                .isEqualTo("NO_ENTRY_TRANSITION");
    }

    @Test
    void unreachableStageIsRejected() {
        WorkflowStage exec = stage("EXEC", StageType.EXECUTION, true, null);
        WorkflowStage orphan = stage("ORPHAN", StageType.EXECUTION, true, null);
        WorkflowStage completion = stage("DONE", StageType.COMPLETION, true, null);
        List<WorkflowStage> stages = List.of(exec, orphan, completion);
        List<WorkflowTransition> transitions = List.of(
                transition(null, exec.getId(), ConditionType.ALWAYS),
                transition(exec.getId(), completion.getId(), ConditionType.ALWAYS));

        assertThatThrownBy(() -> validator.validate(stages, transitions))
                .isInstanceOf(IllegalStateTransitionException.class)
                .extracting(ex -> ((IllegalStateTransitionException) ex).getCode())
                .isEqualTo("UNREACHABLE_STAGE");
    }

    @Test
    void cycleInTransitionGraphIsRejected() {
        WorkflowStage exec = stage("EXEC", StageType.EXECUTION, true, null);
        WorkflowStage mid = stage("MID", StageType.EXECUTION, true, null);
        WorkflowStage completion = stage("DONE", StageType.COMPLETION, true, null);
        List<WorkflowStage> stages = List.of(exec, mid, completion);
        List<WorkflowTransition> transitions = List.of(
                transition(null, exec.getId(), ConditionType.ALWAYS),
                transition(exec.getId(), mid.getId(), ConditionType.ALWAYS),
                transition(mid.getId(), exec.getId(), ConditionType.MANUAL_DECISION),
                transition(mid.getId(), completion.getId(), ConditionType.ALWAYS));

        assertThatThrownBy(() -> validator.validate(stages, transitions))
                .isInstanceOf(IllegalStateTransitionException.class)
                .extracting(ex -> ((IllegalStateTransitionException) ex).getCode())
                .isEqualTo("TRANSITION_CYCLE");
    }

    @Test
    void parallelGroupWithNoRequiredMemberIsRejected() {
        WorkflowStage routing = stage("ROUTING", StageType.ROUTING, true, null);
        WorkflowStage execA = stage("EXEC_A", StageType.EXECUTION, false, "PG");
        WorkflowStage execB = stage("EXEC_B", StageType.EXECUTION, false, "PG");
        WorkflowStage completion = stage("DONE", StageType.COMPLETION, true, null);
        List<WorkflowStage> stages = List.of(routing, execA, execB, completion);
        List<WorkflowTransition> transitions = List.of(
                transition(null, routing.getId(), ConditionType.ALWAYS),
                transition(routing.getId(), execA.getId(), ConditionType.ALWAYS),
                transition(routing.getId(), execB.getId(), ConditionType.ALWAYS),
                transition(execA.getId(), completion.getId(), ConditionType.ALL_REQUIRED_PARALLEL_TASKS_DONE),
                transition(execB.getId(), completion.getId(), ConditionType.ALL_REQUIRED_PARALLEL_TASKS_DONE));

        assertThatThrownBy(() -> validator.validate(stages, transitions))
                .isInstanceOf(IllegalStateTransitionException.class)
                .extracting(ex -> ((IllegalStateTransitionException) ex).getCode())
                .isEqualTo("PARALLEL_GROUP_NO_REQUIRED_MEMBER");
    }

    @Test
    void approvalRequiredWithoutModeIsRejected() {
        WorkflowStage exec = stage("EXEC", StageType.EXECUTION, true, null, true, null, null, null);
        WorkflowStage completion = stage("DONE", StageType.COMPLETION, true, null);
        List<WorkflowStage> stages = List.of(exec, completion);
        List<WorkflowTransition> transitions = List.of(
                transition(null, exec.getId(), ConditionType.ALWAYS),
                transition(exec.getId(), completion.getId(), ConditionType.ALWAYS));

        assertThatThrownBy(() -> validator.validate(stages, transitions))
                .isInstanceOf(IllegalStateTransitionException.class)
                .extracting(ex -> ((IllegalStateTransitionException) ex).getCode())
                .isEqualTo("APPROVAL_MODE_MISSING");
    }

    @Test
    void noCompletionStageIsRejected() {
        WorkflowStage exec = stage("EXEC", StageType.EXECUTION, true, null);
        List<WorkflowStage> stages = List.of(exec);
        List<WorkflowTransition> transitions = List.of(transition(null, exec.getId(), ConditionType.ALWAYS));

        assertThatThrownBy(() -> validator.validate(stages, transitions))
                .isInstanceOf(IllegalStateTransitionException.class)
                .extracting(ex -> ((IllegalStateTransitionException) ex).getCode())
                .isEqualTo("INVALID_COMPLETION_STAGE_COUNT");
    }

    @Test
    void expeditedDeadlineExceedingStandardIsRejected() {
        WorkflowStage exec = stage("EXEC", StageType.EXECUTION, true, null, false, null, 3, 5);
        WorkflowStage completion = stage("DONE", StageType.COMPLETION, true, null);
        List<WorkflowStage> stages = List.of(exec, completion);
        List<WorkflowTransition> transitions = List.of(
                transition(null, exec.getId(), ConditionType.ALWAYS),
                transition(exec.getId(), completion.getId(), ConditionType.ALWAYS));

        assertThatThrownBy(() -> validator.validate(stages, transitions))
                .isInstanceOf(IllegalStateTransitionException.class)
                .extracting(ex -> ((IllegalStateTransitionException) ex).getCode())
                .isEqualTo("EXPEDITED_DEADLINE_EXCEEDS_STANDARD");
    }
}
