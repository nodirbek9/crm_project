package uz.ithunter.crm.applicant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Single table, typed by {@link ApplicantType} (spec 15.2, PLAN_REVIEW H6). Maps to {@code applicant}
 * in V5. Individual and legal-entity fields are both nullable columns here - which set is mandatory
 * is enforced by Bean Validation groups on the request DTOs ({@code applicant.validation}) AND by
 * {@code ck_applicant_individual}/{@code ck_applicant_legal} in the database, never by this entity.
 */
@Entity
@Table(name = "applicant")
@Getter
@Setter
@NoArgsConstructor
public class Applicant {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private ApplicantType type;

    // individual
    @Column(name = "last_name", length = 120)
    private String lastName;

    @Column(name = "first_name", length = 120)
    private String firstName;

    @Column(name = "middle_name", length = 120)
    private String middleName;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(name = "passport_series", length = 10)
    private String passportSeries;

    @Column(name = "passport_number", length = 20)
    private String passportNumber;

    @Column(name = "pinfl", length = 20)
    private String pinfl;

    // legal entity
    @Column(name = "org_name", length = 255)
    private String orgName;

    @Column(name = "tin", length = 20)
    private String tin;

    @Column(name = "representative_full_name", length = 200)
    private String representativeFullName;

    @Column(name = "representative_position", length = 200)
    private String representativePosition;

    @Column(name = "power_of_attorney_ref", length = 255)
    private String powerOfAttorneyRef;

    // common
    @Column(name = "address", nullable = false, length = 500)
    private String address;

    @Column(name = "phone", nullable = false, length = 40)
    private String phone;

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    // DB-trigger managed (tr_applicant_updated -> set_updated_at()); never written by Hibernate.
    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;
}
