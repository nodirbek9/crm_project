package uz.ithunter.crm.casemodule.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * What the applicant sees in the portal (spec 4.19, 15.5 - 15.7, 15.13; API_SPEC.md 4).
 *
 * <p>The field list is closed. API_SPEC.md 4 says this response "deliberately contains <b>only</b>"
 * these fields, and spec 15.13 is the reason: internal stage names, executors, internal comments, the
 * CRM-calculated price and performed-works data must never reach the applicant. So do not add a field
 * here to solve a staff-side need - {@code CaseResponse} is the internal view and it exists for that.
 *
 * <p>Nothing in this record is an entity or contains an id of one, except {@code finalDocument.id},
 * which the applicant needs in order to download their own certificate.
 *
 * <p>ASSUMPTIONS.md A28: {@code contract}, {@code payment}, {@code finalDocument} and
 * {@code notifications} stay null/empty until Phase 8 (accounting), Phase 10 (documents) and Phase 12
 * (notifications) exist. The shape ships now so the applicant contract does not change under the
 * portal later; the values arrive as those phases land.
 */
public record ApplicantTrackingResponse(
        String applicationNumber,
        Instant submittedAt,
        String serviceName,
        ExternalStageView externalStage,
        ContractView contract,
        PaymentView payment,
        ReturnedForCorrectionView returnedForCorrection,
        FinalDocumentView finalDocument,
        List<NotificationView> notifications) {

    /**
     * The applicant-facing stage label only. Never the internal {@code workflow_stage} code or name -
     * several internal stages collapse into one of these (spec 5.11, 15.6), and which internal step
     * is running is not the applicant's business.
     */
    public record ExternalStageView(String code, String nameForApplicant) {
    }

    /** The signed contract as the applicant knows it (spec 15.5). Populated from Phase 8 onwards. */
    public record ContractView(String number, LocalDate date, BigDecimal actualAmount, String currency) {
    }

    /**
     * Payment state (spec 15.5). {@code confirmedAmount} is what accounting has confirmed received -
     * never the CRM-calculated price, which spec 15.13 keeps internal.
     */
    public record PaymentView(String status, BigDecimal confirmedAmount, BigDecimal debtAmount) {
    }

    /** Why the application came back and by when it must be fixed (spec 4.7, 15.8; test I-07). */
    public record ReturnedForCorrectionView(String reason, String remarks, LocalDate dueDate) {
    }

    /** The issued certificate/protocol the applicant may download (spec 15.7). Phase 10 onwards. */
    public record FinalDocumentView(UUID id, String name, Instant issuedAt) {
    }

    /** Portal notifications addressed to this applicant (spec 15.10). Phase 12 onwards. */
    public record NotificationView(String type, String message, Instant sentAt) {
    }
}
