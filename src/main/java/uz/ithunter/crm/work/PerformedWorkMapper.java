package uz.ithunter.crm.work;

import org.springframework.stereotype.Component;
import uz.ithunter.crm.work.dto.PerformedWorkResponse;

@Component
public class PerformedWorkMapper {
    public PerformedWorkResponse toResponse(PerformedWork pw) {
        PerformedWorkResponse r = new PerformedWorkResponse();
        r.setId(pw.getId());
        r.setCaseId(pw.getCaseId());
        r.setWorkTypeId(pw.getWorkTypeId());
        r.setCaseStageId(pw.getCaseStageId());
        r.setWorkflowStageId(pw.getWorkflowStageId());
        r.setServiceId(pw.getServiceId());
        r.setDepartmentId(pw.getDepartmentId());
        r.setExecutorUserId(pw.getExecutorUserId());
        r.setProcessingMode(pw.getProcessingMode() != null ? pw.getProcessingMode().name() : null);
        r.setPerformedAt(pw.getPerformedAt());
        r.setRecordedAt(pw.getRecordedAt());
        r.setRecordedById(pw.getRecordedById());
        r.setSupportingDocumentVersionId(pw.getSupportingDocumentVersionId());
        r.setInvoiceReference(pw.getInvoiceReference());
        r.setContractAmountBracket(pw.getContractAmountBracket() != null ? pw.getContractAmountBracket().name() : null);
        r.setCountable(pw.isCountable());
        return r;
    }
}
