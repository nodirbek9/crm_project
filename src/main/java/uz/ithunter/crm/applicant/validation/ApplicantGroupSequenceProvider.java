package uz.ithunter.crm.applicant.validation;

import java.util.ArrayList;
import java.util.List;
import org.hibernate.validator.spi.group.DefaultGroupSequenceProvider;
import uz.ithunter.crm.applicant.ApplicantType;

/**
 * Selects {@link IndividualGroup} or {@link LegalEntityGroup} based on the request's own
 * {@code type} field (spec 15.2, FINAL_IMPLEMENTATION_ORDER.md Phase 5's named mechanism). Applies
 * to both create and update requests via {@link TypedApplicantRequest} so there is exactly one
 * provider implementation.
 */
public class ApplicantGroupSequenceProvider implements DefaultGroupSequenceProvider<TypedApplicantRequest> {

    @Override
    public List<Class<?>> getValidationGroups(Class<?> klass, TypedApplicantRequest object) {
        List<Class<?>> groups = new ArrayList<>();
        groups.add(klass != null ? klass : TypedApplicantRequest.class);
        if (object != null && object.type() == ApplicantType.LEGAL_ENTITY) {
            groups.add(LegalEntityGroup.class);
        } else {
            groups.add(IndividualGroup.class);
        }
        return groups;
    }
}
