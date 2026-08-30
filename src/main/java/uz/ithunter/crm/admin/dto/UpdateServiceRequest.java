package uz.ithunter.crm.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.Set;

public record UpdateServiceRequest(
        @NotBlank String name, String description, boolean active,
        boolean contractRequired, boolean paymentRequired, boolean standaloneLaboratory,
        @NotEmpty Set<String> submissionChannels) {
}
