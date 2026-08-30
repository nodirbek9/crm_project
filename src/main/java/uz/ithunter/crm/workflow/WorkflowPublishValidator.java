package uz.ithunter.crm.workflow;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import uz.ithunter.crm.shared.exception.IllegalStateTransitionException;

/**
 * The seven publish-time rules of WORKFLOW_ENGINE_DESIGN.md 2, run against a draft's loaded
 * stage/transition graph before {@code WorkflowDefinitionService.publish} is allowed to proceed.
 * Fails fast on the first violated rule with a specific 422 code.
 *
 * <p>Rule 3 ("no cycle except explicit revision edges") simplifies to a plain cycle check here:
 * revision (spec 7.13, WORKFLOW_ENGINE_DESIGN.md 8) reopens the same {@code case_stage} row via
 * the {@code revision_allowed} flag - it is never modeled as a {@code workflow_transition} row, so
 * there is no such thing as a "revision edge" to exempt in this data model.
 */
@Component
public class WorkflowPublishValidator {

    public void validate(List<WorkflowStage> stages, List<WorkflowTransition> transitions) {
        UUID entryStageId = checkExactlyOneEntryTransition(transitions);
        checkEveryStageReachable(stages, transitions, entryStageId);
        checkNoCycle(stages, transitions);
        checkParallelGroupsHaveRequiredMember(stages);
        checkApprovalModeSetWhenRequired(stages);
        checkExactlyOneCompletionStage(stages);
        checkExpeditedDeadlineWithinStandard(stages);
    }

    private UUID checkExactlyOneEntryTransition(List<WorkflowTransition> transitions) {
        List<WorkflowTransition> entries = transitions.stream().filter(t -> t.getFromStageId() == null).toList();
        if (entries.isEmpty()) {
            throw new IllegalStateTransitionException("NO_ENTRY_TRANSITION", "The route has no entry transition");
        }
        if (entries.size() > 1) {
            throw new IllegalStateTransitionException("MULTIPLE_ENTRY_TRANSITIONS", "The route has more than one entry transition");
        }
        return entries.get(0).getToStageId();
    }

    private void checkEveryStageReachable(List<WorkflowStage> stages, List<WorkflowTransition> transitions, UUID entryStageId) {
        Map<UUID, List<UUID>> adjacency = buildAdjacency(transitions);
        Set<UUID> visited = new HashSet<>();
        Deque<UUID> queue = new ArrayDeque<>();
        queue.add(entryStageId);
        while (!queue.isEmpty()) {
            UUID current = queue.poll();
            if (!visited.add(current)) {
                continue;
            }
            queue.addAll(adjacency.getOrDefault(current, List.of()));
        }
        List<String> unreachable = stages.stream()
                .filter(s -> !visited.contains(s.getId()))
                .map(WorkflowStage::getCode)
                .toList();
        if (!unreachable.isEmpty()) {
            throw new IllegalStateTransitionException("UNREACHABLE_STAGE",
                    "Stage(s) not reachable from the entry transition: " + unreachable);
        }
    }

    private void checkNoCycle(List<WorkflowStage> stages, List<WorkflowTransition> transitions) {
        Map<UUID, List<UUID>> adjacency = buildAdjacency(transitions);
        Set<UUID> visited = new HashSet<>();
        Set<UUID> onStack = new HashSet<>();
        for (WorkflowStage stage : stages) {
            if (hasCycle(stage.getId(), adjacency, visited, onStack)) {
                throw new IllegalStateTransitionException("TRANSITION_CYCLE", "The route's transition graph contains a cycle");
            }
        }
    }

    private boolean hasCycle(UUID node, Map<UUID, List<UUID>> adjacency, Set<UUID> visited, Set<UUID> onStack) {
        if (onStack.contains(node)) {
            return true;
        }
        if (visited.contains(node)) {
            return false;
        }
        visited.add(node);
        onStack.add(node);
        for (UUID next : adjacency.getOrDefault(node, List.of())) {
            if (hasCycle(next, adjacency, visited, onStack)) {
                return true;
            }
        }
        onStack.remove(node);
        return false;
    }

    private void checkParallelGroupsHaveRequiredMember(List<WorkflowStage> stages) {
        Map<String, List<WorkflowStage>> byGroup = new HashMap<>();
        for (WorkflowStage stage : stages) {
            if (stage.getParallelGroup() != null) {
                byGroup.computeIfAbsent(stage.getParallelGroup(), g -> new ArrayList<>()).add(stage);
            }
        }
        for (var entry : byGroup.entrySet()) {
            boolean hasRequired = entry.getValue().stream().anyMatch(WorkflowStage::isRequired);
            if (!hasRequired) {
                throw new IllegalStateTransitionException("PARALLEL_GROUP_NO_REQUIRED_MEMBER",
                        "Parallel group '" + entry.getKey() + "' has no required = true member");
            }
        }
    }

    private void checkApprovalModeSetWhenRequired(List<WorkflowStage> stages) {
        for (WorkflowStage stage : stages) {
            if (stage.isApprovalRequired() && stage.getApprovalMode() == null) {
                throw new IllegalStateTransitionException("APPROVAL_MODE_MISSING",
                        "Stage '" + stage.getCode() + "' requires approval but has no approval mode");
            }
        }
    }

    private void checkExactlyOneCompletionStage(List<WorkflowStage> stages) {
        long completionCount = stages.stream().filter(s -> s.getStageType() == StageType.COMPLETION).count();
        if (completionCount != 1) {
            throw new IllegalStateTransitionException("INVALID_COMPLETION_STAGE_COUNT",
                    "Expected exactly one COMPLETION stage, found " + completionCount);
        }
    }

    private void checkExpeditedDeadlineWithinStandard(List<WorkflowStage> stages) {
        for (WorkflowStage stage : stages) {
            Integer deadline = stage.getDeadlineDays();
            Integer expedited = stage.getExpeditedDeadlineDays();
            if (deadline != null && expedited != null && expedited > deadline) {
                throw new IllegalStateTransitionException("EXPEDITED_DEADLINE_EXCEEDS_STANDARD",
                        "Stage '" + stage.getCode() + "' has expeditedDeadlineDays > deadlineDays");
            }
        }
    }

    private Map<UUID, List<UUID>> buildAdjacency(List<WorkflowTransition> transitions) {
        Map<UUID, List<UUID>> adjacency = new HashMap<>();
        for (WorkflowTransition t : transitions) {
            if (t.getFromStageId() == null) {
                continue;
            }
            adjacency.computeIfAbsent(t.getFromStageId(), k -> new ArrayList<>()).add(t.getToStageId());
        }
        return adjacency;
    }
}
