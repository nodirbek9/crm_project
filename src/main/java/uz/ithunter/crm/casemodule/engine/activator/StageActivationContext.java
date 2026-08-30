package uz.ithunter.crm.casemodule.engine.activator;

import uz.ithunter.crm.casemodule.CaseStage;
import uz.ithunter.crm.casemodule.ElectronicCase;
import uz.ithunter.crm.workflow.Workflow;
import uz.ithunter.crm.workflow.WorkflowStage;

/**
 * What an activator is handed. {@code workflow} is the case's PINNED version (spec 5.12), so an
 * activator reading {@code payment_waiting_days} or {@code allow_execution_before_full_payment} sees
 * the rules the case was registered under, not whatever was published since.
 */
public record StageActivationContext(
        ElectronicCase electronicCase,
        CaseStage stage,
        WorkflowStage stageConfig,
        Workflow workflow) {
}
