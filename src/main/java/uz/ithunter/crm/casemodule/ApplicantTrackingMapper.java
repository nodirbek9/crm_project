package uz.ithunter.crm.casemodule;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.stereotype.Component;
import uz.ithunter.crm.casemodule.dto.ApplicantTrackingResponse;
import uz.ithunter.crm.workflow.ExternalStage;

/**
 * Builds the applicant tracking payload - and it is the only thing in the codebase that may
 * (spec 4.19, 15.13; API_SPEC.md 4; test S-07).
 *
 * <p>Two rules keep this honest, both of them structural rather than a matter of care:
 *
 * <ol>
 *   <li>the input is {@link CaseTrackingProjection}, a query projection over six columns, not
 *       {@code ElectronicCase} - so an internal field cannot be copied across by accident because it
 *       was never loaded;
 *   <li>the output is {@link ApplicantTrackingResponse}, a closed record - so a new internal field
 *       cannot appear in the JSON by inheriting from the internal response.
 * </ol>
 *
 * <p>No repository, no {@code SecurityContext}: whoever calls this has already proven the caller may
 * see this case ({@code CaseAccessPolicy}). This class only shapes data.
 */
@Component
public class ApplicantTrackingMapper {

    /**
     * @param projection the six applicant-visible columns of the case
     * @param externalStage the single collapsed external stage, or {@code null} when the case sits on
     *     no stage the applicant is shown
     * @param correctionReason the reviewing specialist's reason, shown only while the case is RETURNED
     * @param correctionRemarks free-text remarks from the check's checklist, may be {@code null}
     */
    public ApplicantTrackingResponse toResponse(CaseTrackingProjection projection,
            ExternalStage externalStage, String correctionReason, String correctionRemarks) {
        if (projection == null) {
            throw new IllegalArgumentException("projection is required");
        }
        return new ApplicantTrackingResponse(
                projection.applicationNumber(),
                projection.submittedAt(),
                projection.serviceName(),
                toExternalStageView(externalStage),
                // ASSUMPTIONS.md A28: contract, payment, final document and notifications belong to
                // Phases 8, 10 and 12. Null here is the truth, and a truthful null is better than a
                // zero that reads like "you owe nothing".
                null,
                null,
                toCorrectionView(projection, correctionReason, correctionRemarks),
                null,
                List.of());
    }

    private ApplicantTrackingResponse.ExternalStageView toExternalStageView(ExternalStage externalStage) {
        if (externalStage == null) {
            return null;
        }
        return new ApplicantTrackingResponse.ExternalStageView(
                externalStage.getCode(), externalStage.getNameForApplicant());
    }

    /**
     * Only present while the case is actually RETURNED (test I-07). Once the applicant resubmits, the
     * block disappears rather than lingering as a stale accusation.
     */
    private ApplicantTrackingResponse.ReturnedForCorrectionView toCorrectionView(
            CaseTrackingProjection projection, String reason, String remarks) {
        if (projection.status() != CaseStatus.RETURNED) {
            return null;
        }
        return new ApplicantTrackingResponse.ReturnedForCorrectionView(reason, remarks, toDueDate(projection));
    }

    /**
     * The correction deadline as a calendar date. Fixed to UTC rather than the server default zone so
     * that the same case renders the same date in every environment (ASSUMPTIONS.md A28); a real
     * per-applicant timezone is a portal concern, not a backend one.
     */
    private LocalDate toDueDate(CaseTrackingProjection projection) {
        return projection.dueAt() == null ? null : projection.dueAt().atZone(ZoneOffset.UTC).toLocalDate();
    }
}
