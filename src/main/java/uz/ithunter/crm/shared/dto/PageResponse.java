package uz.ithunter.crm.shared.dto;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/**
 * The paged response shape fixed by API_SPEC.md 0:
 * {@code PageResponse<T>{content,page,size,totalElements,totalPages}}.
 *
 * <p>Spring Data's own {@code Page} is deliberately not serialized: its JSON shape is an
 * implementation detail that has changed between Spring Data versions, and API_SPEC.md pins these
 * five fields.
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }

    /** Maps entities to DTOs while keeping the paging metadata of the source page. */
    public static <E, D> PageResponse<D> of(Page<E> page, Function<E, D> mapper) {
        return new PageResponse<>(page.getContent().stream().map(mapper).toList(), page.getNumber(),
                page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
