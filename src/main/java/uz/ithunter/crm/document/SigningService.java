package uz.ithunter.crm.document;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.ithunter.crm.audit.AuditAction;
import uz.ithunter.crm.audit.AuditEvent;
import uz.ithunter.crm.audit.AuditWriter;
import uz.ithunter.crm.auth.CustomUserPrincipal;
import uz.ithunter.crm.user.RoleCode;
import uz.ithunter.crm.casemodule.CaseAccessPolicy;
import uz.ithunter.crm.casemodule.CaseStageRepository;
import uz.ithunter.crm.casemodule.CaseStageStatus;
import uz.ithunter.crm.casemodule.engine.WorkflowEngine;
import uz.ithunter.crm.document.dto.DocumentVersionResponse;
import uz.ithunter.crm.document.dto.SignRequest;
import uz.ithunter.crm.shared.exception.AccessDeniedDomainException;
import uz.ithunter.crm.shared.exception.IllegalStateTransitionException;
import uz.ithunter.crm.shared.exception.NotFoundException;
import uz.ithunter.crm.workflow.StageType;
import uz.ithunter.crm.workflow.WorkflowStageRepository;

import java.time.Instant;
import java.util.UUID;

@Service
public class SigningService {
    private final DocumentRepository documentRepository;
    private final DocumentVersionRepository versionRepository;
    private final CaseAccessPolicy caseAccessPolicy;
    private final DocumentMapper mapper;
    private final AuditWriter auditWriter;
    private final WorkflowEngine workflowEngine;
    private final CaseStageRepository caseStageRepository;
    private final WorkflowStageRepository workflowStageRepository;

    public SigningService(DocumentRepository documentRepository, DocumentVersionRepository versionRepository,
                          CaseAccessPolicy caseAccessPolicy, DocumentMapper mapper, AuditWriter auditWriter,
                          WorkflowEngine workflowEngine, CaseStageRepository caseStageRepository,
                          WorkflowStageRepository workflowStageRepository) {
        this.documentRepository = documentRepository;
        this.versionRepository = versionRepository;
        this.caseAccessPolicy = caseAccessPolicy;
        this.mapper = mapper;
        this.auditWriter = auditWriter;
        this.workflowEngine = workflowEngine;
        this.caseStageRepository = caseStageRepository;
        this.workflowStageRepository = workflowStageRepository;
    }

    @Transactional
    public DocumentVersionResponse sign(UUID documentId, int versionNo, SignRequest request, CustomUserPrincipal principal) {
        if (!caseAccessPolicy.rolesOf(principal).contains(RoleCode.HEAD_OF_CERTIFICATION_BODY)) {
            throw new AccessDeniedDomainException("SIGN_NOT_PERMITTED", "Only Head of Certification Body can sign documents");
        }

        DocumentVersion version = versionRepository.findByDocumentIdAndVersionNo(documentId, versionNo)
                .orElseThrow(() -> new NotFoundException("VERSION_NOT_FOUND", "Document version not found"));
        caseAccessPolicy.requireCanView(principal, version.getDocument().getElectronicCase());

        if (version.getStatus() != DocumentVersionStatus.ENDORSED) {
            throw new IllegalStateTransitionException("APPROVAL_NOT_COMPLETED", "Cannot sign document before approval completes");
        }

        version.setStatus(DocumentVersionStatus.SIGNED);
        version.setSignedById(principal.userId());
        version.setSignedAt(Instant.now());
        versionRepository.save(version);

        Document doc = version.getDocument();
        doc.setStatus(DocumentStatus.SIGNED);
        doc.setCurrentVersionId(version.getId());
        doc.setUpdatedAt(Instant.now());
        documentRepository.save(doc);

        auditWriter.write(AuditEvent.forCase(
                AuditAction.DOCUMENT_SIGNED,
                "DocumentVersion",
                version.getId(),
                doc.getElectronicCase().getId(),
                null,
                java.util.Map.of("documentId", documentId.toString(), "versionNo", versionNo,
                        "signedById", principal.userId().toString()),
                request.getNote()
        ));

        caseStageRepository.findByCaseId(doc.getElectronicCase().getId()).stream()
                .filter(stage -> stage.getStatus() == CaseStageStatus.ACTIVE)
                .filter(stage -> workflowStageRepository.findById(stage.getWorkflowStageId())
                        .map(ws -> ws.getStageType() == StageType.SIGNING)
                        .orElse(false))
                .findFirst()
                .ifPresent(stage -> workflowEngine.completeStage(doc.getElectronicCase().getId(), stage.getWorkflowStageId()));

        return mapper.toVersionResponse(version);
    }
}
