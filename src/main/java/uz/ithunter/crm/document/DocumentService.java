package uz.ithunter.crm.document;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.ithunter.crm.audit.AuditAction;
import uz.ithunter.crm.audit.AuditEvent;
import uz.ithunter.crm.audit.AuditWriter;
import uz.ithunter.crm.auth.CustomUserPrincipal;
import uz.ithunter.crm.casemodule.CaseAccessPolicy;
import uz.ithunter.crm.casemodule.ElectronicCase;
import uz.ithunter.crm.casemodule.ElectronicCaseRepository;
import uz.ithunter.crm.document.dto.CreateDocumentRequest;
import uz.ithunter.crm.document.dto.CreateDocumentVersionRequest;
import uz.ithunter.crm.document.dto.DocumentResponse;
import uz.ithunter.crm.document.dto.DocumentSummary;
import uz.ithunter.crm.document.dto.DocumentVersionResponse;
import uz.ithunter.crm.shared.exception.NotFoundException;
import uz.ithunter.crm.shared.exception.ValidationException;
import uz.ithunter.crm.shared.exception.FieldIssue;
import uz.ithunter.crm.task.Task;
import uz.ithunter.crm.task.TaskRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class DocumentService {
    private final DocumentRepository documentRepository;
    private final DocumentVersionRepository versionRepository;
    private final ElectronicCaseRepository caseRepository;
    private final CaseAccessPolicy caseAccessPolicy;
    private final DocumentMapper mapper;
    private final DocumentStoragePort storagePort;
    private final AuditWriter auditWriter;
    private final TaskRepository taskRepository;

    public DocumentService(DocumentRepository documentRepository, DocumentVersionRepository versionRepository,
                           ElectronicCaseRepository caseRepository, CaseAccessPolicy caseAccessPolicy,
                           DocumentMapper mapper, DocumentStoragePort storagePort, AuditWriter auditWriter,
                           TaskRepository taskRepository) {
        this.documentRepository = documentRepository;
        this.versionRepository = versionRepository;
        this.caseRepository = caseRepository;
        this.caseAccessPolicy = caseAccessPolicy;
        this.mapper = mapper;
        this.storagePort = storagePort;
        this.auditWriter = auditWriter;
        this.taskRepository = taskRepository;
    }

    @Transactional
    public DocumentResponse createDocument(UUID caseId, CreateDocumentRequest request, CustomUserPrincipal principal) {
        ElectronicCase eCase = caseRepository.findById(caseId)
                .orElseThrow(() -> new NotFoundException("CASE_NOT_FOUND", "Case not found"));
        caseAccessPolicy.requireCanView(principal, eCase);

        Document doc = new Document();
        doc.setElectronicCase(eCase);
        doc.setDocumentType(request.getDocumentType());
        doc.setTitle(request.getTitle());
        doc.setCreatedById(principal.userId());
        if (request.getTaskId() != null) {
            // Nothing else in this codebase ever sets document.task_id - without this, Phase 11's
            // PerformedWorkRecorder could never find a supporting document through the real API.
            Task task = taskRepository.findById(request.getTaskId())
                    .orElseThrow(() -> new NotFoundException("TASK_NOT_FOUND", "Task not found"));
            doc.setTask(task);
        }
        doc = documentRepository.save(doc);

        auditWriter.write(AuditEvent.forCase(
                AuditAction.DOCUMENT_CREATED,
                "Document",
                doc.getId(),
                doc.getElectronicCase().getId(),
                null, null, null
        ));

        return mapper.toResponse(doc, List.of());
    }

    @Transactional(readOnly = true)
    public List<DocumentSummary> listDocuments(UUID caseId, CustomUserPrincipal principal) {
        ElectronicCase eCase = caseRepository.findById(caseId)
                .orElseThrow(() -> new NotFoundException("CASE_NOT_FOUND", "Case not found"));
        caseAccessPolicy.requireCanView(principal, eCase);

        return documentRepository.findByElectronicCaseId(caseId).stream()
                .map(mapper::toSummary)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public DocumentResponse getDocument(UUID documentId, CustomUserPrincipal principal) {
        Document doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new NotFoundException("DOCUMENT_NOT_FOUND", "Document not found"));
        caseAccessPolicy.requireCanView(principal, doc.getElectronicCase());

        List<DocumentVersion> versions = versionRepository.findByDocumentIdOrderByVersionNoDesc(documentId);
        return mapper.toResponse(doc, versions);
    }

    @Transactional
    public DocumentVersionResponse createVersion(UUID documentId, CreateDocumentVersionRequest request, CustomUserPrincipal principal) {
        Document doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new NotFoundException("DOCUMENT_NOT_FOUND", "Document not found"));
        caseAccessPolicy.requireCanView(principal, doc.getElectronicCase());

        if (!storagePort.exists(request.getContentRef())) {
            throw new ValidationException("VALIDATION_FAILED", "Content ref not found in storage",
                    List.of(new FieldIssue("contentRef", "must reference existing stored content")));
        }

        List<DocumentVersion> versions = versionRepository.findByDocumentIdOrderByVersionNoDesc(documentId);
        int nextVersionNo = versions.isEmpty() ? 1 : versions.get(0).getVersionNo() + 1;

        if (nextVersionNo > 1) {
            if (request.getRevisionReason() == null || request.getRevisionReason().trim().isEmpty()) {
                throw new ValidationException("VALIDATION_FAILED", "Revision reason required for version > 1",
                        List.of(new FieldIssue("revisionReason", "must not be blank when superseding a version")));
            }
        }

        DocumentVersion version = new DocumentVersion();
        version.setDocument(doc);
        version.setVersionNo(nextVersionNo);
        version.setContentRef(request.getContentRef());
        version.setContentHash(request.getContentHash());
        version.setFileName(request.getFileName());
        version.setMimeType(request.getMimeType());
        version.setSizeBytes(request.getSizeBytes());
        version.setFields(request.getFields() != null ? request.getFields() : "{}");
        version.setCreatedById(principal.userId());
        
        if (nextVersionNo > 1) {
            DocumentVersion previous = versions.get(0);
            version.setSupersedesId(previous.getId());
            version.setRevisionReason(request.getRevisionReason());
            // D-01: creating a second version supersedes the first. A version already in a
            // terminal SIGNED state can never reach here in practice (tr_docver_guard would
            // reject changing its status, and uq_docver_signed_once means a document with a
            // signed version has nothing left to endorse) - guarded anyway rather than assumed.
            if (previous.getStatus() != DocumentVersionStatus.SIGNED) {
                previous.setStatus(DocumentVersionStatus.SUPERSEDED);
                versionRepository.save(previous);
            }
        }

        version = versionRepository.save(version);

        doc.setCurrentVersionId(version.getId());
        doc.setUpdatedAt(Instant.now());
        documentRepository.save(doc);

        auditWriter.write(AuditEvent.forCase(
                AuditAction.DOCUMENT_VERSION_CREATED,
                "DocumentVersion",
                version.getId(),
                doc.getElectronicCase().getId(),
                null, null, null
        ));

        return mapper.toVersionResponse(version);
    }

    @Transactional(readOnly = true)
    public DocumentVersionResponse getVersion(UUID documentId, int versionNo, CustomUserPrincipal principal) {
        Document doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new NotFoundException("DOCUMENT_NOT_FOUND", "Document not found"));
        caseAccessPolicy.requireCanView(principal, doc.getElectronicCase());

        DocumentVersion version = versionRepository.findByDocumentIdAndVersionNo(documentId, versionNo)
                .orElseThrow(() -> new NotFoundException("VERSION_NOT_FOUND", "Document version not found"));
        return mapper.toVersionResponse(version);
    }
}
