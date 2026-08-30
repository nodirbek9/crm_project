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
 * Spec 15.2, API_SPEC.md 2. {@code password} is this session's resolution (ASSUMPTIONS.md A17) of
 * the otherwise-unspecified self-registration mechanics: a successful {@code POST /applicants}
 * atomically creates the {@code Applicant} row and a linked {@code User{role=APPLICANT}}.
 *
 * <p>Each type-specific field carries two constraints: required for its own type
 * ({@code @NotBlank}/{@code @NotNull}), forbidden for the other ({@code @Null}) - this is what
 * makes "wrong-type fields present" a validation failure too, not just "right-type fields missing."
 */
@GroupSequenceProvider(ApplicantGroupSequenceProvider.class)
public record CreateApplicantRequest(
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
        @NotBlank String password) implements TypedApplicantRequest {
}
