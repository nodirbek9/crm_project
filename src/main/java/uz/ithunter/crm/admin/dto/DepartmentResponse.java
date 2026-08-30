package uz.ithunter.crm.admin.dto;

import java.time.Instant;
import java.util.UUID;

public record DepartmentResponse(
        UUID id, String code, String name, UUID parentId, UUID headUserId, boolean active,
        Instant createdAt, Instant updatedAt) {
}
