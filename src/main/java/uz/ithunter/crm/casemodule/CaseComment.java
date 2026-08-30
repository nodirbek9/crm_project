package uz.ithunter.crm.casemodule;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * An internal working comment (spec 13.5.1, 17.4, 17.8, PLAN_REVIEW M6), mapping to
 * {@code case_comment} in V5.
 *
 * <p>Deliberately NOT the same thing as an endorsement remark: a remark is bound to the document
 * version it was written against and survives revisions (Phase 10), while this is organisational
 * chatter on the case. Neither is ever shown to the applicant -
 * {@code ApplicantTrackingMapper} has no field for it, and test S-07 asserts that on the raw JSON.
 *
 * <p>{@code documentVersionId} carries no FK until V8 creates {@code document_version}, so it stays a
 * plain UUID column here; the constraint is added by that migration, not by this entity.
 *
 * <p>No {@code updated_at} and no update trigger: a comment is an append-only record of what somebody
 * said at a point in time.
 */
@Entity
@Table(name = "case_comment")
@Getter
@Setter
@NoArgsConstructor
public class CaseComment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "case_id", nullable = false)
    private UUID caseId;

    @Column(name = "document_version_id")
    private UUID documentVersionId;

    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    @Column(name = "author_department_id")
    private UUID authorDepartmentId;

    // text column (unbounded) - no length attribute on purpose.
    @Column(name = "body", nullable = false)
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false, length = 20)
    private CommentVisibility visibility = CommentVisibility.INTERNAL;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;
}
