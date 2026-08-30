package uz.ithunter.crm.applicant.validation;

import uz.ithunter.crm.applicant.ApplicantType;

/** Implemented by both create/update request DTOs so one {@link ApplicantGroupSequenceProvider} serves both. */
public interface TypedApplicantRequest {
    ApplicantType type();
}
