package uz.ithunter.crm.casemodule.engine;

import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Component;
import uz.ithunter.crm.shared.domain.ProcessingMode;
import uz.ithunter.crm.workflow.Workflow;
import uz.ithunter.crm.workflow.WorkflowStage;

/**
 * Turns a configured deadline in days into an absolute instant (spec 5.8, ASSUMPTIONS.md A5).
 *
 * <p>A {@code @Component} with no dependencies at all, so test U-09 can do {@code new
 * DeadlineCalculator()} with no Spring context and no database - the same style
 * {@code WorkflowPublishValidator} established in Phase 6.
 *
 * <p>The EXPEDITED rule, in order of precedence:
 * <ol>
 *   <li>an explicit {@code expedited_deadline_days} on the stage wins - configuration beats a
 *       calculation;</li>
 *   <li>otherwise the traditional deadline is halved by integer division with a floor of one day, so
 *       a 5-day stage becomes 2 and a 1-day stage stays 1 rather than collapsing to 0.</li>
 * </ol>
 * A {@code null} deadline means "not time-boxed" and yields {@code null} rather than {@code now} -
 * a stage with no configured deadline must not be born overdue.
 */
@Component
public class DeadlineCalculator {

    /** {@code null} when the stage is not time-boxed. */
    public Instant stageDueAt(WorkflowStage stage, ProcessingMode mode, Instant from) {
        Integer days = stageDeadlineDays(stage, mode);
        return days == null ? null : from.plus(Duration.ofDays(days));
    }

    /** {@code null} when the route sets no overall deadline. */
    public Instant caseDueAt(Workflow workflow, ProcessingMode mode, Instant from) {
        Integer days = applyMode(workflow.getTotalDeadlineDays(), null, mode);
        return days == null ? null : from.plus(Duration.ofDays(days));
    }

    public Integer stageDeadlineDays(WorkflowStage stage, ProcessingMode mode) {
        return applyMode(stage.getDeadlineDays(), stage.getExpeditedDeadlineDays(), mode);
    }

    private Integer applyMode(Integer traditionalDays, Integer configuredExpeditedDays, ProcessingMode mode) {
        if (mode != ProcessingMode.EXPEDITED) {
            // A null mode means accounting has not chosen yet (spec 1.9) - treat it as TRADITIONAL
            // rather than guessing the shorter deadline and reporting a case overdue too early.
            return traditionalDays;
        }
        if (configuredExpeditedDays != null) {
            return configuredExpeditedDays;
        }
        if (traditionalDays == null) {
            return null;
        }
        return Math.max(1, traditionalDays / 2);
    }
}
