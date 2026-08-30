package uz.ithunter.crm.casemodule.dto;

import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /applications/{id}/register} (API_SPEC.md 3). Everything the registration needs
 * is already on the application and its route; {@code note} is the registrar's optional remark, which
 * is not stored as case data but carried into the {@code CASE_REGISTERED} audit row as its reason -
 * "why was this registered by hand on a Friday evening" is an audit question, not a case field.
 */
public record RegisterApplicationRequest(@Size(max = 1000) String note) {
}
