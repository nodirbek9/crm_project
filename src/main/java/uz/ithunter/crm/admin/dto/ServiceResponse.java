package uz.ithunter.crm.admin.dto;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record ServiceResponse(
        UUID id, String code, String name, String description, boolean active,
        boolean contractRequired, boolean paymentRequired, boolean standaloneLaboratory,
        Set<String> submissionChannels, Instant createdAt, Instant updatedAt) {
}
