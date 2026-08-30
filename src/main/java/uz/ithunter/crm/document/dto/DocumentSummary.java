package uz.ithunter.crm.document.dto;

import lombok.Data;
import java.time.Instant;
import java.util.UUID;

@Data
public class DocumentSummary {
    private UUID id;
    private UUID caseId;
    private String documentType;
    private String title;
    private String status;
    private UUID currentVersionId;
    private Instant createdAt;
    private Instant updatedAt;
}
