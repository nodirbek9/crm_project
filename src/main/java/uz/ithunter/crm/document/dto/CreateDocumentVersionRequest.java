package uz.ithunter.crm.document.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class CreateDocumentVersionRequest {
    @NotBlank
    private String contentRef;
    
    @NotBlank
    @Pattern(regexp = "^[0-9a-f]{64}$", message = "contentHash must be a valid SHA-256 hex string")
    private String contentHash;
    
    private String fileName;
    private String mimeType;
    private Long sizeBytes;
    
    private String fields = "{}";
    private String revisionReason;
}
