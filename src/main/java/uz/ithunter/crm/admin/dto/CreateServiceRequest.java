package uz.ithunter.crm.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.Set;

public record CreateServiceRequest(
        @NotBlank String code, @NotBlank String name, String description,
        boolean contractRequired, boolean paymentRequired, boolean standaloneLaboratory,
        @NotEmpty Set<String> submissionChannels) {
}
