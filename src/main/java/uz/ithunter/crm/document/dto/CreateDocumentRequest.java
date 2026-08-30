package uz.ithunter.crm.document.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;
import lombok.Data;

@Data
public class CreateDocumentRequest {
    @NotBlank
    private String documentType;
    @NotBlank
    private String title;

    /**
     * Optional link to the {@code Task} whose result this document formalises (spec 8.3's
     * "supporting document" linkage - {@code Document.taskId} is how
     * {@code work.PerformedWorkRecorder} finds a supporting document version at stage
     * completion). Nothing else in this codebase populates {@code document.task_id}, so without
     * this field the linkage could never be set through the real API at all.
     */
    private UUID taskId;
}
