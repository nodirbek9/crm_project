package uz.ithunter.crm.auth.dto;

import java.util.List;
import java.util.UUID;

public record UserSummary(UUID id, String email, String fullName, List<String> roles) {
}
