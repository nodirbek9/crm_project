package uz.ithunter.crm.document.dto;

import lombok.Data;
import java.time.Instant;
import java.util.UUID;

@Data
public class DocumentVersionResponse {
    private UUID id;
    private UUID documentId;
    private int versionNo;
    private String contentRef;
    private String contentHash;
    private String fileName;
    private String mimeType;
    private Long sizeBytes;
    private String fields;
    private String status;
    private UUID createdById;
    private Instant createdAt;
    private UUID supersedesId;
    private String revisionReason;
    private UUID signedById;
    private Instant signedAt;
}
