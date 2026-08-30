package uz.ithunter.crm.applicant;

import java.util.Set;
import java.util.UUID;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.ithunter.crm.applicant.dto.ApplicantResponse;
import uz.ithunter.crm.applicant.dto.CreateApplicantRequest;
import uz.ithunter.crm.applicant.dto.UpdateApplicantRequest;
import uz.ithunter.crm.auth.CustomUserPrincipal;
import uz.ithunter.crm.shared.exception.AccessDeniedDomainException;
import uz.ithunter.crm.shared.exception.ConflictException;
import uz.ithunter.crm.shared.exception.NotFoundException;
import uz.ithunter.crm.shared.exception.ValidationException;
import uz.ithunter.crm.user.Role;
import uz.ithunter.crm.user.RoleCode;
import uz.ithunter.crm.user.RoleRepository;
import uz.ithunter.crm.user.User;
import uz.ithunter.crm.user.UserRepository;
import uz.ithunter.crm.user.UserStatus;

/**
 * ASSUMPTIONS.md A17: {@link #create} is this session's resolution of an otherwise-unspecified gap
 * - it is the applicant self-registration flow, atomically creating {@link Applicant} and a linked
 * {@code User{role=APPLICANT}} in one transaction. {@code get}/{@code update} apply the two-layer
 * authorization SECURITY_SPEC.md 5 describes: the controller's {@code @PreAuthorize} is layer 1
 * (holds {@code APPLICATION:VIEW} at all, which {@code APPLICANT} itself already does), and the
 * explicit ownership check here is layer 2 - a 404, not 403, when an applicant reaches for someone
 * else's row (SECURITY_SPEC.md 6's enumeration-safety rule).
 */
@Service
public class ApplicantService {

    private final ApplicantRepository applicantRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public ApplicantService(ApplicantRepository applicantRepository, UserRepository userRepository,
            RoleRepository roleRepository, PasswordEncoder passwordEncoder) {
        this.applicantRepository = applicantRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public ApplicantResponse create(CreateApplicantRequest request) {
        if (request.pinfl() != null && applicantRepository.findByPinfl(request.pinfl()).isPresent()) {
            throw new ConflictException("PINFL_ALREADY_EXISTS", "An applicant with this PINFL already exists");
        }
        if (request.tin() != null && applicantRepository.findByTin(request.tin()).isPresent()) {
            throw new ConflictException("TIN_ALREADY_EXISTS", "An applicant with this TIN already exists");
        }
        if (userRepository.findByEmail(request.email()).isPresent()) {
            throw new ConflictException("EMAIL_ALREADY_EXISTS", "A user with this email already exists");
        }

        Applicant applicant = new Applicant();
        applyFields(applicant, request.type(), request.lastName(), request.firstName(), request.middleName(),
                request.birthDate(), request.passportSeries(), request.passportNumber(), request.pinfl(),
                request.orgName(), request.tin(), request.representativeFullName(), request.representativePosition(),
                request.powerOfAttorneyRef(), request.address(), request.phone(), request.email());
        applicant = applicantRepository.save(applicant);

        Role applicantRole = roleRepository.findByCode(RoleCode.APPLICANT)
                .orElseThrow(() -> new IllegalStateException("Seeded role missing: APPLICANT"));
        User user = new User();
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFullName(displayName(request.type(), request.lastName(), request.firstName(), request.orgName()));
        user.setApplicantId(applicant.getId());
        user.setStatus(UserStatus.ACTIVE);
        user.setRoles(Set.of(applicantRole));
        userRepository.save(user);

        return toResponse(applicant);
    }

    @Transactional(readOnly = true)
    public ApplicantResponse get(UUID id, CustomUserPrincipal principal) {
        Applicant applicant = findOrThrow(id, principal);
        return toResponse(applicant);
    }

    @Transactional
    public ApplicantResponse update(UUID id, UpdateApplicantRequest request, CustomUserPrincipal principal) {
        Applicant applicant = findOrThrow(id, principal);

        boolean isOwner = principal.applicantId() != null && principal.applicantId().equals(id);
        if (!isOwner && !hasAuthority("USER_ADMIN:EDIT")) {
            throw new AccessDeniedDomainException("PERMISSION_DENIED", "Only the applicant themselves or an admin may edit this profile");
        }
        if (request.type() != applicant.getType()) {
            throw new ValidationException("VALIDATION_FAILED", "Applicant type cannot be changed after creation");
        }
        if (applicant.getVersion() != request.version()) {
            throw new ObjectOptimisticLockingFailureException(Applicant.class, id);
        }

        applyFields(applicant, request.type(), request.lastName(), request.firstName(), request.middleName(),
                request.birthDate(), request.passportSeries(), request.passportNumber(), request.pinfl(),
                request.orgName(), request.tin(), request.representativeFullName(), request.representativePosition(),
                request.powerOfAttorneyRef(), request.address(), request.phone(), request.email());
        applicant = applicantRepository.save(applicant);
        return toResponse(applicant);
    }

    private Applicant findOrThrow(UUID id, CustomUserPrincipal principal) {
        Applicant applicant = applicantRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Applicant not found"));
        if (principal.applicantId() != null && !principal.applicantId().equals(id)) {
            throw new NotFoundException("Applicant not found");
        }
        return applicant;
    }

    private boolean hasAuthority(String code) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(code::equals);
    }

    private void applyFields(Applicant applicant, ApplicantType type, String lastName, String firstName,
            String middleName, java.time.LocalDate birthDate, String passportSeries, String passportNumber,
            String pinfl, String orgName, String tin, String representativeFullName, String representativePosition,
            String powerOfAttorneyRef, String address, String phone, String email) {
        applicant.setType(type);
        applicant.setLastName(lastName);
        applicant.setFirstName(firstName);
        applicant.setMiddleName(middleName);
        applicant.setBirthDate(birthDate);
        applicant.setPassportSeries(passportSeries);
        applicant.setPassportNumber(passportNumber);
        applicant.setPinfl(pinfl);
        applicant.setOrgName(orgName);
        applicant.setTin(tin);
        applicant.setRepresentativeFullName(representativeFullName);
        applicant.setRepresentativePosition(representativePosition);
        applicant.setPowerOfAttorneyRef(powerOfAttorneyRef);
        applicant.setAddress(address);
        applicant.setPhone(phone);
        applicant.setEmail(email);
    }

    private String displayName(ApplicantType type, String lastName, String firstName, String orgName) {
        if (type == ApplicantType.LEGAL_ENTITY) {
            return orgName;
        }
        return (lastName + " " + firstName).trim();
    }

    private ApplicantResponse toResponse(Applicant a) {
        return new ApplicantResponse(a.getId(), a.getType(), a.getLastName(), a.getFirstName(), a.getMiddleName(),
                a.getBirthDate(), a.getPassportSeries(), a.getPassportNumber(), a.getPinfl(), a.getOrgName(),
                a.getTin(), a.getRepresentativeFullName(), a.getRepresentativePosition(), a.getPowerOfAttorneyRef(),
                a.getAddress(), a.getPhone(), a.getEmail(), a.getVersion(), a.getCreatedAt(), a.getUpdatedAt());
    }
}
