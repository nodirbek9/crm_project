package uz.ithunter.crm.applicant.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import uz.ithunter.crm.applicant.ApplicantType;

public record ApplicantResponse(
        UUID id,
        ApplicantType type,
        String lastName, String firstName, String middleName, LocalDate birthDate,
        String passportSeries, String passportNumber, String pinfl,
        String orgName, String tin, String representativeFullName, String representativePosition,
        String powerOfAttorneyRef,
        String address, String phone, String email,
        long version, Instant createdAt, Instant updatedAt) {
}
