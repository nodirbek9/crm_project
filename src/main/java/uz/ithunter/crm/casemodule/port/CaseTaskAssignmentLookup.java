package uz.ithunter.crm.casemodule.port;

import java.util.UUID;

/**
 * The seam between {@link uz.ithunter.crm.casemodule.CaseAccessPolicy}'s DEPARTMENT_HEAD/SPECIALIST
 * task-existence clauses (SECURITY_SPEC.md 5, ASSUMPTIONS.md A26/A39) and Phase 9's {@code task}
 * module - the same reason {@code casemodule.port.PriceConfirmationChecker} and
 * {@code casemodule.engine.port.PaymentStateProvider} exist: {@code casemodule} must not depend on
 * a later phase's module, even though that phase depends on {@code casemodule} (external review
 * finding: a direct {@code ObjectProvider<TaskRepository>} dependency here was the one place that
 * boundary was crossed without a port).
 */
public interface CaseTaskAssignmentLookup {

    /** True when this case has a task currently assigned to this exact user. */
    boolean existsAssignedToUser(UUID caseId, UUID userId);

    /** True when this case has a task currently assigned to this department. */
    boolean existsAssignedToDepartment(UUID caseId, UUID departmentId);
}
