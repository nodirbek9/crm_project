package uz.ithunter.crm.shared.exception;

import java.time.Instant;
import java.util.List;

/** The one error shape used everywhere, per API_SPEC.md 9. */
public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String code,
        String message,
        String path,
        String traceId,
        List<FieldIssue> details) {
}
