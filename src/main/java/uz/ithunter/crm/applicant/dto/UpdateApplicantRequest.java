package uz.ithunter.crm.applicant.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import java.time.LocalDate;
import org.hibernate.validator.group.GroupSequenceProvider;
import uz.ithunter.crm.applicant.ApplicantType;
import uz.ithunter.crm.applicant.validation.ApplicantGroupSequenceProvider;
import uz.ithunter.crm.applicant.validation.IndividualGroup;
import uz.ithunter.crm.applicant.validation.LegalEntityGroup;
import uz.ithunter.crm.applicant.validation.TypedApplicantRequest;

/**
 * {@code type} is echoed (not changeable - the service 400s if it doesn't match the stored row) so
 * {@link ApplicantGroupSequenceProvider} has something to key off of, reusing the exact same
 * validation shape as {@link CreateApplicantRequest}. {@code version} is the optimistic-locking
 * echo per API_SPEC.md 0.
 */
@GroupSequenceProvider(ApplicantGroupSequenceProvider.class)
public record UpdateApplicantRequest(
        @NotNull ApplicantType type,

        @NotBlank(groups = IndividualGroup.class) @Null(groups = LegalEntityGroup.class) String lastName,
        @NotBlank(groups = IndividualGroup.class) @Null(groups = LegalEntityGroup.class) String firstName,
        @Null(groups = LegalEntityGroup.class) String middleName,
        @NotNull(groups = IndividualGroup.class) @Null(groups = LegalEntityGroup.class) LocalDate birthDate,
        @NotBlank(groups = IndividualGroup.class) @Null(groups = LegalEntityGroup.class) String passportSeries,
        @NotBlank(groups = IndividualGroup.class) @Null(groups = LegalEntityGroup.class) String passportNumber,
        @NotBlank(groups = IndividualGroup.class) @Null(groups = LegalEntityGroup.class) String pinfl,

        @NotBlank(groups = LegalEntityGroup.class) @Null(groups = IndividualGroup.class) String orgName,
        @NotBlank(groups = LegalEntityGroup.class) @Null(groups = IndividualGroup.class) String tin,
        @NotBlank(groups = LegalEntityGroup.class) @Null(groups = IndividualGroup.class) String representativeFullName,
        @NotBlank(groups = LegalEntityGroup.class) @Null(groups = IndividualGroup.class) String representativePosition,
        @Null(groups = IndividualGroup.class) String powerOfAttorneyRef,

        @NotBlank String address,
        @NotBlank String phone,
        @NotBlank @Email String email,
        long version) implements TypedApplicantRequest {
}
