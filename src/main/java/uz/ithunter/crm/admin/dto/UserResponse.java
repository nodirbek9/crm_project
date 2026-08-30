package uz.ithunter.crm.admin.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String fullName,
        UUID departmentId,
        UUID positionId,
        List<String> roles,
        String status,
        long version,
        Instant createdAt,
        Instant updatedAt) {
}
