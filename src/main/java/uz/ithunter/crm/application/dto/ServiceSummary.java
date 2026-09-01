package uz.ithunter.crm.application.dto;

import java.util.Set;
import java.util.UUID;

/**
 * The public service catalog an applicant chooses from before creating an application
 * (API_SPEC.md 2's own scenario assumes this exists). Deliberately not {@code ServiceResponse}
 * from {@code admin.dto}: that DTO is the full admin CRUD shape (contract/payment flags, audit
 * timestamps) gated behind {@code REFERENCE_DATA:VIEW}, which no non-admin role holds - this is
 * the minimal, non-sensitive subset any authenticated caller may see.
 */
public record ServiceSummary(UUID id, String code, String name, Set<String> submissionChannels) {
}
