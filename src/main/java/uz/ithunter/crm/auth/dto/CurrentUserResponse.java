package uz.ithunter.crm.auth.dto;

import java.util.List;
import java.util.UUID;

public record CurrentUserResponse(
        UUID id,
        String email,
        String fullName,
        List<String> roles,
        List<String> permissions,
        UUID departmentId,
        UUID applicantId) {
}
