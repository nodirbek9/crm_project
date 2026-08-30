package uz.ithunter.crm.task;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskResultRepository extends JpaRepository<TaskResult, UUID> {

    /** Full version chain, oldest-first. Used for GET /tasks/{id}/results. */
    List<TaskResult> findByTaskIdOrderByVersionNoAsc(UUID taskId);

    /** The live result — at most one row is in SUBMITTED or APPROVED state (uq_task_result_live). */
    Optional<TaskResult> findByTaskIdAndStatusIn(UUID taskId, List<TaskResultStatus> statuses);

    /** Count of results for a task — used to determine the next versionNo. */
    int countByTaskId(UUID taskId);
}
