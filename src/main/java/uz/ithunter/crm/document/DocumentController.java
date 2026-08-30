package uz.ithunter.crm.document;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import uz.ithunter.crm.auth.CustomUserPrincipal;
import uz.ithunter.crm.document.dto.CreateDocumentRequest;
import uz.ithunter.crm.document.dto.CreateDocumentVersionRequest;
import uz.ithunter.crm.document.dto.DocumentResponse;
import uz.ithunter.crm.document.dto.DocumentSummary;
import uz.ithunter.crm.document.dto.DocumentVersionResponse;
import uz.ithunter.crm.document.dto.SignRequest;

import java.util.List;
import java.util.UUID;

@RestController
public class DocumentController {
    
    private final DocumentService documentService;
    private final SigningService signingService;

    public DocumentController(DocumentService documentService, SigningService signingService) {
        this.documentService = documentService;
        this.signingService = signingService;
    }

    @PostMapping("/api/cases/{id}/documents")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('DOCUMENT:CREATE')")
    public DocumentResponse createDocument(@PathVariable UUID id, 
                                           @Valid @RequestBody CreateDocumentRequest request,
                                           @AuthenticationPrincipal CustomUserPrincipal principal) {
        return documentService.createDocument(id, request, principal);
    }

    @GetMapping("/api/cases/{id}/documents")
    @PreAuthorize("hasAuthority('DOCUMENT:VIEW')")
    public List<DocumentSummary> listDocuments(@PathVariable UUID id,
                                               @AuthenticationPrincipal CustomUserPrincipal principal) {
        return documentService.listDocuments(id, principal);
    }

    @GetMapping("/api/documents/{id}")
    @PreAuthorize("hasAuthority('DOCUMENT:VIEW')")
    public DocumentResponse getDocument(@PathVariable UUID id,
                                        @AuthenticationPrincipal CustomUserPrincipal principal) {
        return documentService.getDocument(id, principal);
    }

    @PostMapping("/api/documents/{id}/versions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('DOCUMENT:EDIT')")
    public DocumentVersionResponse createVersion(@PathVariable UUID id, 
                                                 @Valid @RequestBody CreateDocumentVersionRequest request,
                                                 @AuthenticationPrincipal CustomUserPrincipal principal) {
        return documentService.createVersion(id, request, principal);
    }

    @GetMapping("/api/documents/{id}/versions/{versionNo}")
    @PreAuthorize("hasAuthority('DOCUMENT:VIEW')")
    public DocumentVersionResponse getVersion(@PathVariable UUID id,
                                              @PathVariable int versionNo,
                                              @AuthenticationPrincipal CustomUserPrincipal principal) {
        return documentService.getVersion(id, versionNo, principal);
    }

    @PostMapping("/api/documents/{id}/versions/{versionNo}/sign")
    @PreAuthorize("hasAuthority('DOCUMENT:SIGN')")
    public DocumentVersionResponse sign(@PathVariable UUID id, 
                                        @PathVariable int versionNo,
                                        @Valid @RequestBody(required = false) SignRequest request,
                                        @AuthenticationPrincipal CustomUserPrincipal principal) {
        if (request == null) {
            request = new SignRequest();
        }
        return signingService.sign(id, versionNo, request, principal);
    }
}
