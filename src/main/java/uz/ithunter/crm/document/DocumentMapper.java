package uz.ithunter.crm.document;

import org.springframework.stereotype.Component;
import uz.ithunter.crm.document.dto.DocumentResponse;
import uz.ithunter.crm.document.dto.DocumentSummary;
import uz.ithunter.crm.document.dto.DocumentVersionResponse;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class DocumentMapper {

    public DocumentSummary toSummary(Document document) {
        DocumentSummary summary = new DocumentSummary();
        summary.setId(document.getId());
        summary.setCaseId(document.getElectronicCase().getId());
        summary.setDocumentType(document.getDocumentType());
        summary.setTitle(document.getTitle());
        summary.setStatus(document.getStatus().name());
        summary.setCurrentVersionId(document.getCurrentVersionId());
        summary.setCreatedAt(document.getCreatedAt());
        summary.setUpdatedAt(document.getUpdatedAt());
        return summary;
    }

    public DocumentVersionResponse toVersionResponse(DocumentVersion version) {
        DocumentVersionResponse response = new DocumentVersionResponse();
        response.setId(version.getId());
        response.setDocumentId(version.getDocument().getId());
        response.setVersionNo(version.getVersionNo());
        response.setContentRef(version.getContentRef());
        response.setContentHash(version.getContentHash());
        response.setFileName(version.getFileName());
        response.setMimeType(version.getMimeType());
        response.setSizeBytes(version.getSizeBytes());
        response.setFields(version.getFields());
        response.setStatus(version.getStatus().name());
        response.setCreatedById(version.getCreatedById());
        response.setCreatedAt(version.getCreatedAt());
        response.setSupersedesId(version.getSupersedesId());
        response.setRevisionReason(version.getRevisionReason());
        response.setSignedById(version.getSignedById());
        response.setSignedAt(version.getSignedAt());
        return response;
    }

    public DocumentResponse toResponse(Document document, List<DocumentVersion> versions) {
        DocumentResponse response = new DocumentResponse();
        response.setId(document.getId());
        response.setCaseId(document.getElectronicCase().getId());
        response.setDocumentType(document.getDocumentType());
        response.setTitle(document.getTitle());
        response.setStatus(document.getStatus().name());
        response.setCurrentVersionId(document.getCurrentVersionId());
        response.setCreatedAt(document.getCreatedAt());
        response.setUpdatedAt(document.getUpdatedAt());
        if (versions != null) {
            response.setVersions(versions.stream().map(this::toVersionResponse).collect(Collectors.toList()));
        }
        return response;
    }
}
