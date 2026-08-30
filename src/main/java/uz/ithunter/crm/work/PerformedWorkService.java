package uz.ithunter.crm.work;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.ithunter.crm.auth.CustomUserPrincipal;
import uz.ithunter.crm.casemodule.CaseAccessPolicy;
import uz.ithunter.crm.casemodule.ElectronicCase;
import uz.ithunter.crm.casemodule.ElectronicCaseRepository;
import uz.ithunter.crm.shared.exception.NotFoundException;
import uz.ithunter.crm.work.dto.PerformedWorkResponse;

@Service
public class PerformedWorkService {

    /** Matches no real work_type row - forces zero search results for an unresolved workTypeCode. */
    private static final UUID NO_SUCH_WORK_TYPE = new UUID(0L, 0L);

    private final PerformedWorkRepository performedWorkRepository;
    private final ElectronicCaseRepository caseRepository;
    private final CaseAccessPolicy caseAccessPolicy;
    private final PerformedWorkMapper mapper;
    private final WorkTypeRepository workTypeRepository;

    public PerformedWorkService(PerformedWorkRepository performedWorkRepository,
                                ElectronicCaseRepository caseRepository,
                                CaseAccessPolicy caseAccessPolicy,
                                PerformedWorkMapper mapper,
                                WorkTypeRepository workTypeRepository) {
        this.performedWorkRepository = performedWorkRepository;
        this.caseRepository = caseRepository;
        this.caseAccessPolicy = caseAccessPolicy;
        this.mapper = mapper;
        this.workTypeRepository = workTypeRepository;
    }

    @Transactional(readOnly = true)
    public List<PerformedWorkResponse> listByCaseId(UUID caseId, CustomUserPrincipal principal) {
        ElectronicCase eCase = caseRepository.findById(caseId)
                .orElseThrow(() -> new NotFoundException("CASE_NOT_FOUND", "Case not found"));
        caseAccessPolicy.requireCanView(principal, eCase);
        return performedWorkRepository.findByCaseId(caseId).stream()
                .map(mapper::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Page<PerformedWorkResponse> search(UUID caseId, UUID executorId, UUID departmentId,
                                               String workTypeCode, Instant from, Instant to,
                                               CustomUserPrincipal principal, Pageable pageable) {
        // Resolve workTypeCode to UUID if provided. A code that matches nothing must narrow the
        // search to zero rows, not silently fall back to "no work-type filter" - the repository's
        // dynamic query reads workTypeId == null as "unfiltered", so an unresolved code needs a
        // sentinel that matches no real work_type row, not null.
        UUID workTypeId = null;
        if (workTypeCode != null) {
            workTypeId = workTypeRepository.findByCode(workTypeCode)
                    .map(WorkType::getId)
                    .orElse(NO_SUCH_WORK_TYPE);
        }
        return performedWorkRepository.search(caseId, executorId, departmentId, workTypeId, from, to, pageable)
                .map(mapper::toResponse);
    }
}
