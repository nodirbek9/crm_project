package uz.ithunter.crm.admin;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.ithunter.crm.admin.dto.CreateDepartmentRequest;
import uz.ithunter.crm.admin.dto.DepartmentResponse;
import uz.ithunter.crm.admin.dto.UpdateDepartmentRequest;
import uz.ithunter.crm.audit.AuditAction;
import uz.ithunter.crm.audit.AuditEvent;
import uz.ithunter.crm.audit.AuditWriter;
import uz.ithunter.crm.shared.dto.PageResponse;
import uz.ithunter.crm.shared.exception.NotFoundException;
import uz.ithunter.crm.user.Department;
import uz.ithunter.crm.user.DepartmentRepository;

@Service
public class AdminDepartmentService {

    private final DepartmentRepository departmentRepository;
    private final AuditWriter auditWriter;

    public AdminDepartmentService(DepartmentRepository departmentRepository, AuditWriter auditWriter) {
        this.departmentRepository = departmentRepository;
        this.auditWriter = auditWriter;
    }

    @Transactional(readOnly = true)
    public PageResponse<DepartmentResponse> list(Pageable pageable) {
        return PageResponse.of(departmentRepository.findAll(pageable), this::toResponse);
    }

    @Transactional
    public DepartmentResponse create(CreateDepartmentRequest request) {
        Department department = new Department();
        department.setCode(request.code());
        department.setName(request.name());
        department.setParentId(request.parentId());
        department.setHeadUserId(request.headUserId());
        department.setActive(true);
        department = departmentRepository.save(department);

        auditWriter.write(AuditEvent.administrative(AuditAction.DEPARTMENT_CHANGED, "Department",
                department.getId(), null, snapshot(department)));
        return toResponse(department);
    }

    @Transactional
    public DepartmentResponse update(UUID id, UpdateDepartmentRequest request) {
        Department department = departmentRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Department not found"));
        Map<String, Object> oldValue = snapshot(department);

        department.setName(request.name());
        department.setParentId(request.parentId());
        department.setHeadUserId(request.headUserId());
        department.setActive(request.active());
        department = departmentRepository.save(department);

        auditWriter.write(AuditEvent.administrative(AuditAction.DEPARTMENT_CHANGED, "Department",
                department.getId(), oldValue, snapshot(department)));
        return toResponse(department);
    }

    private Map<String, Object> snapshot(Department department) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("code", department.getCode());
        map.put("name", department.getName());
        map.put("parentId", department.getParentId());
        map.put("headUserId", department.getHeadUserId());
        map.put("active", department.isActive());
        return map;
    }

    private DepartmentResponse toResponse(Department department) {
        return new DepartmentResponse(department.getId(), department.getCode(), department.getName(),
                department.getParentId(), department.getHeadUserId(), department.isActive(),
                department.getCreatedAt(), department.getUpdatedAt());
    }
}
