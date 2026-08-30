package uz.ithunter.crm.casemodule;

import java.time.Instant;
import java.util.UUID;

/**
 * The <b>only</b> case data the applicant-facing tracking view is allowed to load (spec 4.19, 15.5 -
 * 15.7, 15.13).
 *
 * <p>This is a separate projection on purpose. The alternative - loading a full {@code CaseResponse}
 * and nulling the internal fields before serialising - is exactly the pattern SECURITY_SPEC.md and
 * PLAN_REVIEW forbid: the day someone adds a field to the internal response, the applicant silently
 * starts receiving it. Here, the internal fields are not fetched from the database at all, so there
 * is nothing to forget to hide. Test S-07 asserts the resulting JSON directly.
 *
 * <p>{@code workflowId} and {@code caseId} are technical ids the service needs to resolve the
 * external stage; {@link ApplicantTrackingMapper} does not copy them into the response.
 */
public record CaseTrackingProjection(
        UUID caseId,
        UUID workflowId,
        CaseStatus status,
        String applicationNumber,
        Instant submittedAt,
        String serviceName,
        Instant dueAt) {
}
