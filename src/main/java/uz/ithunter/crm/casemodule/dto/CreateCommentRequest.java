package uz.ithunter.crm.casemodule.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Body of {@code POST /cases/{id}/comments} (API_SPEC.md 4, spec 13.5.1).
 *
 * <p>No {@code visibility} field: every comment is INTERNAL in this system. Spec 13.5.1 and 17.8 are
 * unambiguous that these remarks are staff-to-staff, so making visibility a request parameter would
 * only create a way to get it wrong.
 */
public record CreateCommentRequest(
        @NotBlank @Size(max = 4000) String body,
        UUID documentVersionId) {
}
