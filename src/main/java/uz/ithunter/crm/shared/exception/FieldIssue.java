package uz.ithunter.crm.shared.exception;

/** One entry of {@link ErrorResponse#details()}, matching API_SPEC.md 9's {@code {field, issue}} shape. */
public record FieldIssue(String field, String issue) {
}
