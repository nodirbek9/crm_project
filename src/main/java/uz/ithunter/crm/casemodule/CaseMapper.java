package uz.ithunter.crm.casemodule;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import uz.ithunter.crm.applicant.Applicant;
import uz.ithunter.crm.applicant.ApplicantRepository;
import uz.ithunter.crm.applicant.ApplicantType;
import uz.ithunter.crm.application.Application;
import uz.ithunter.crm.application.ApplicationRepository;
import uz.ithunter.crm.application.ServiceRepository;
import uz.ithunter.crm.casemodule.dto.CaseItemResponse;
import uz.ithunter.crm.casemodule.dto.CaseResponse;
import uz.ithunter.crm.casemodule.dto.CaseSummary;
import uz.ithunter.crm.casemodule.dto.CommentResponse;
import uz.ithunter.crm.casemodule.dto.StageTimelineItem;
import uz.ithunter.crm.user.Department;
import uz.ithunter.crm.user.DepartmentRepository;
import uz.ithunter.crm.user.User;
import uz.ithunter.crm.user.UserRepository;
import uz.ithunter.crm.workflow.Workflow;
import uz.ithunter.crm.workflow.WorkflowRepository;
import uz.ithunter.crm.workflow.WorkflowStage;
import uz.ithunter.crm.workflow.WorkflowStageRepository;

/**
 * Assembles the INTERNAL case read models (API_SPEC.md 4). Two rules it exists to keep:
 * no JPA entity ever leaves a controller, and no response DTO is built by hand in two places.
 *
 * <p>It reads through repositories rather than through JPA relations because the entities carry raw
 * UUID foreign keys by design ({@code spring.jpa.open-in-view: false}, FINAL_DOMAIN_MODEL.md) - the
 * price is explicit lookups here, the payoff is that a mapper can never trigger a lazy load in the
 * middle of serialising a response.
 *
 * <p>The list path batches those lookups ({@code findAllById} once per referenced table, one query for
 * every page's ACTIVE stages) instead of resolving each row on its own, which is the difference between
 * 6 queries and 300 for a 50-case page.
 *
 * <p>{@code CaseResponse.finance} is null throughout Phase 7 - ASSUMPTIONS.md A28.
 */
@Component
public class CaseMapper {

    private final ApplicationRepository applicationRepository;
    private final ApplicantRepository applicantRepository;
    private final ServiceRepository serviceRepository;
    private final WorkflowRepository workflowRepository;
    private final WorkflowStageRepository workflowStageRepository;
    private final DepartmentRepository departmentRepository;
    private final CaseStageRepository caseStageRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public CaseMapper(ApplicationRepository applicationRepository, ApplicantRepository applicantRepository,
            ServiceRepository serviceRepository, WorkflowRepository workflowRepository,
            WorkflowStageRepository workflowStageRepository, DepartmentRepository departmentRepository,
            CaseStageRepository caseStageRepository, UserRepository userRepository, ObjectMapper objectMapper) {
        this.applicationRepository = applicationRepository;
        this.applicantRepository = applicantRepository;
        this.serviceRepository = serviceRepository;
        this.workflowRepository = workflowRepository;
        this.workflowStageRepository = workflowStageRepository;
        this.departmentRepository = departmentRepository;
        this.caseStageRepository = caseStageRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    public CaseResponse toResponse(ElectronicCase electronicCase) {
        Instant now = Instant.now();
        Application application = applicationRepository.findById(electronicCase.getApplicationId()).orElse(null);
        Applicant applicant = applicantRepository.findById(electronicCase.getApplicantId()).orElse(null);
        uz.ithunter.crm.application.Service service =
                serviceRepository.findById(electronicCase.getServiceId()).orElse(null);
        Workflow workflow = workflowRepository.findById(electronicCase.getWorkflowId()).orElse(null);

        Map<UUID, WorkflowStage> configs = stageConfigs(electronicCase.getWorkflowId());
        List<CaseStage> stages = caseStageRepository.findByCaseId(electronicCase.getId());

        Map<UUID, CaseResponse.StageRef> activeByWorkflowStage = new HashMap<>();
        List<CaseResponse.StageRef> active = new ArrayList<>();
        for (CaseStage stage : stages) {
            if (stage.getStatus() != CaseStageStatus.ACTIVE) {
                continue;
            }
            CaseResponse.StageRef ref = toStageRef(stage, configs.get(stage.getWorkflowStageId()), now);
            activeByWorkflowStage.put(stage.getWorkflowStageId(), ref);
            active.add(ref);
        }
        active.sort(Comparator.comparingInt(CaseResponse.StageRef::sequence));
        List<CaseResponse.StageRef> activeStages = List.copyOf(active);

        // NULL while a parallel group is open (PLAN_REVIEW M1, test W-03) - activeStages then carries
        // the open branches, so the client can render "2 stages in progress" instead of "unknown".
        CaseResponse.StageRef currentStage = electronicCase.getCurrentStageId() == null ? null
                : activeByWorkflowStage.get(electronicCase.getCurrentStageId());

        List<CaseResponse.DepartmentRef> participating =
                departmentRefs(electronicCase.getParticipatingDepartmentIds());

        return new CaseResponse(
                electronicCase.getId(),
                electronicCase.getCaseNumber(),
                application == null ? null : application.getNumber(),
                toApplicantRef(applicant),
                service == null ? null
                        : new CaseResponse.ServiceRef(service.getId(), service.getCode(), service.getName()),
                electronicCase.getStatus(),
                currentStage,
                activeStages,
                electronicCase.getPrimaryCheckCategory(),
                electronicCase.getPrimaryCheckDecision(),
                electronicCase.getProcessingMode(),
                workflow == null ? null
                        : new CaseResponse.WorkflowRef(workflow.getId(), workflow.getCode(), workflow.getVersion()),
                departmentRef(electronicCase.getMainResponsibleDepartmentId()),
                participating,
                electronicCase.getDueAt(),
                electronicCase.getPaymentDueAt(),
                electronicCase.isPaymentOverdue(),
                null,
                electronicCase.getVersion(),
                electronicCase.getCreatedAt());
    }

    /** The page path of {@code GET /cases}: every referenced table is read once, not once per row. */
    public List<CaseSummary> toSummaries(List<ElectronicCase> cases) {
        if (cases.isEmpty()) {
            return List.of();
        }
        Instant now = Instant.now();
        Map<UUID, Application> applications = byId(applicationRepository.findAllById(
                cases.stream().map(ElectronicCase::getApplicationId).toList()), Application::getId);
        Map<UUID, Applicant> applicants = byId(applicantRepository.findAllById(
                cases.stream().map(ElectronicCase::getApplicantId).toList()), Applicant::getId);
        Map<UUID, uz.ithunter.crm.application.Service> services = byId(serviceRepository.findAllById(
                cases.stream().map(ElectronicCase::getServiceId).toList()),
                uz.ithunter.crm.application.Service::getId);

        Map<UUID, WorkflowStage> configs = new HashMap<>();
        cases.stream().map(ElectronicCase::getWorkflowId).distinct()
                .forEach(workflowId -> configs.putAll(stageConfigs(workflowId)));

        Map<UUID, List<CaseStage>> activeByCase = new HashMap<>();
        caseStageRepository
                .findByCaseIdInAndStatus(cases.stream().map(ElectronicCase::getId).toList(), CaseStageStatus.ACTIVE)
                .forEach(stage -> activeByCase.computeIfAbsent(stage.getCaseId(), key -> new ArrayList<>())
                        .add(stage));

        List<CaseSummary> summaries = new ArrayList<>(cases.size());
        for (ElectronicCase electronicCase : cases) {
            List<CaseStage> active = activeByCase.getOrDefault(electronicCase.getId(), List.of());
            WorkflowStage firstActive = active.stream()
                    .map(stage -> configs.get(stage.getWorkflowStageId()))
                    .filter(config -> config != null)
                    .min(Comparator.comparingInt(WorkflowStage::getSequence))
                    .orElse(null);
            Applicant applicant = applicants.get(electronicCase.getApplicantId());
            uz.ithunter.crm.application.Service service = services.get(electronicCase.getServiceId());
            Application application = applications.get(electronicCase.getApplicationId());
            summaries.add(new CaseSummary(
                    electronicCase.getId(),
                    electronicCase.getCaseNumber(),
                    application == null ? null : application.getNumber(),
                    displayName(applicant),
                    service == null ? null : service.getName(),
                    electronicCase.getStatus(),
                    firstActive == null ? null : firstActive.getCode(),
                    firstActive == null ? null : firstActive.getName(),
                    active.size(),
                    electronicCase.getProcessingMode(),
                    electronicCase.getDueAt(),
                    isOverdue(electronicCase, now),
                    electronicCase.getCreatedAt()));
        }
        return summaries;
    }

    /**
     * {@code GET /cases/{id}/timeline} - every stage row, in route order, including the unreached ones.
     *
     * <p>Configs are looked up by the stage ids on the rows, not by the case's current workflow id: a
     * case whose route was changed by a primary-check decision keeps the CANCELLED rows of the route it
     * left, and those belong to a different workflow. Resolving per workflow would render them with a
     * null code and sequence 0, which is exactly the history a reviewer wants to see.
     */
    public List<StageTimelineItem> toTimeline(ElectronicCase electronicCase) {
        Instant now = Instant.now();
        List<CaseStage> stages = caseStageRepository.findByCaseId(electronicCase.getId());
        Map<UUID, WorkflowStage> configs = byId(workflowStageRepository.findAllById(
                stages.stream().map(CaseStage::getWorkflowStageId).distinct().toList()),
                WorkflowStage::getId);
        return stages.stream()
                .map(stage -> toTimelineItem(stage, configs.get(stage.getWorkflowStageId()), now))
                .sorted(Comparator.comparingInt(StageTimelineItem::sequence)
                        .thenComparing(StageTimelineItem::stageCode,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    public CaseItemResponse toItemResponse(CaseItem item) {
        return new CaseItemResponse(item.getId(), item.getLineNo(), item.getItemName(), item.getItemCode(),
                item.getQuantity(), item.getUnit(), item.getObjectAddress(), parseJson(item.getAttributes()));
    }

    public CommentResponse toCommentResponse(CaseComment comment) {
        User author = comment.getAuthorId() == null ? null
                : userRepository.findById(comment.getAuthorId()).orElse(null);
        return new CommentResponse(comment.getId(), comment.getCaseId(), comment.getDocumentVersionId(),
                comment.getAuthorId(), author == null ? null : author.getFullName(),
                comment.getAuthorDepartmentId(), comment.getVisibility(), comment.getBody(),
                comment.getCreatedAt());
    }

    /** Derived, never stored: {@code electronic_case} has no {@code overdue} column, and shouldn't - it
     * would be a cached value that goes stale the moment the clock ticks past {@code due_at}. */
    public boolean isOverdue(ElectronicCase electronicCase, Instant now) {
        return electronicCase.getDueAt() != null
                && electronicCase.getCompletedAt() == null
                && electronicCase.getDueAt().isBefore(now);
    }

    public Map<UUID, WorkflowStage> stageConfigs(UUID workflowId) {
        Map<UUID, WorkflowStage> configs = new LinkedHashMap<>();
        workflowStageRepository.findByWorkflowId(workflowId).forEach(stage -> configs.put(stage.getId(), stage));
        return configs;
    }

    private CaseResponse.StageRef toStageRef(CaseStage stage, WorkflowStage config, Instant now) {
        return new CaseResponse.StageRef(
                stage.getId(),
                config == null ? null : config.getCode(),
                config == null ? null : config.getName(),
                config == null ? null : config.getStageType(),
                config == null ? null : config.getInternalStatusLabel(),
                config == null ? 0 : config.getSequence(),
                stage.getParallelGroup(),
                stage.isRequired(),
                stage.getStatus(),
                stage.getActivatedAt(),
                stage.getDueAt(),
                stageOverdue(stage, now));
    }

    private StageTimelineItem toTimelineItem(CaseStage stage, WorkflowStage config, Instant now) {
        return new StageTimelineItem(
                stage.getId(),
                config == null ? null : config.getCode(),
                config == null ? null : config.getName(),
                config == null ? null : config.getStageType(),
                config == null ? null : config.getInternalStatusLabel(),
                config == null ? 0 : config.getSequence(),
                stage.getParallelGroup(),
                stage.isRequired(),
                stage.getStatus(),
                stage.getActivatedAt(),
                stage.getCompletedAt(),
                stage.getDueAt(),
                stageOverdue(stage, now),
                stage.getActivationCount());
    }

    /**
     * The stored flag OR the clock. The flag is what a future scheduled job sets so that "show me
     * everything overdue" can be an indexed query; until that job runs, an open stage past its deadline
     * is still late, and a staff member should not have to wait for a cron to be told so.
     */
    private boolean stageOverdue(CaseStage stage, Instant now) {
        if (stage.isOverdue()) {
            return true;
        }
        return stage.getDueAt() != null
                && stage.getCompletedAt() == null
                && stage.getStatus() == CaseStageStatus.ACTIVE
                && stage.getDueAt().isBefore(now);
    }

    private CaseResponse.ApplicantRef toApplicantRef(Applicant applicant) {
        if (applicant == null) {
            return null;
        }
        return new CaseResponse.ApplicantRef(applicant.getId(), applicant.getType().name(),
                displayName(applicant), applicant.getTin(), applicant.getPhone());
    }

    private String displayName(Applicant applicant) {
        if (applicant == null) {
            return null;
        }
        if (applicant.getType() == ApplicantType.LEGAL_ENTITY) {
            return applicant.getOrgName();
        }
        return ((applicant.getLastName() == null ? "" : applicant.getLastName()) + " "
                + (applicant.getFirstName() == null ? "" : applicant.getFirstName())).trim();
    }

    private CaseResponse.DepartmentRef departmentRef(UUID departmentId) {
        if (departmentId == null) {
            return null;
        }
        return departmentRepository.findById(departmentId)
                .map(department -> new CaseResponse.DepartmentRef(department.getId(), department.getCode(),
                        department.getName()))
                .orElse(null);
    }

    private List<CaseResponse.DepartmentRef> departmentRefs(Collection<UUID> departmentIds) {
        if (departmentIds == null || departmentIds.isEmpty()) {
            return List.of();
        }
        return departmentRepository.findAllById(departmentIds).stream()
                .sorted(Comparator.comparing(Department::getCode, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(department -> new CaseResponse.DepartmentRef(department.getId(), department.getCode(),
                        department.getName()))
                .toList();
    }

    private <T> Map<UUID, T> byId(Iterable<T> entities, java.util.function.Function<T, UUID> idOf) {
        Map<UUID, T> map = new HashMap<>();
        entities.forEach(entity -> map.put(idOf.apply(entity), entity));
        return map;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJson(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        return objectMapper.readValue(json, Map.class);
    }
}
