package uz.ithunter.crm.audit;

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
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Maps to {@code audit_log} (V10). Append-only by design (spec 20.3): the table has no update or
 * delete path at any level - no API, no {@code crm_app} grant, and a BEFORE UPDATE OR DELETE
 * trigger that raises even for the schema owner. {@code @Immutable} adds the same guarantee on the
 * Hibernate side so an accidental setter call on a loaded row cannot even produce an UPDATE.
 *
 * <p>Two columns are deliberately NOT mapped:
 * <ul>
 *   <li>{@code prev_hash} / {@code row_hash} - computed by the {@code tr_audit_log_chain} trigger
 *       so the application "cannot forge it and cannot forget it" (V10's own comment). They are
 *       read only through {@code verify_audit_chain()}, never through this entity. As a bonus this
 *       avoids a {@code ddl-auto: validate} failure: they are {@code char(64)} (bpchar, JDBC
 *       {@code CHAR}) and a plain {@code String} attribute would be validated as {@code VARCHAR}.
 * </ul>
 *
 * <p>{@code case_id} is nullable on purpose (PLAN_REVIEW C4) - administrative events have no
 * electronic case. Which actions may leave it null is enforced by {@code ck_audit_case_scope} and
 * mirrored in {@link AuditAction#isAdministrative()}.
 */
@Entity
@Table(name = "audit_log")
@Immutable
@Getter
@Setter
@NoArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // bigserial, assigned by the DB. Read-only here; it is the hash chain's ordering key.
    @Column(name = "seq", nullable = false, insertable = false, updatable = false)
    private Long seq;

    @Column(name = "case_id")
    private UUID caseId;

    @Column(name = "task_id")
    private UUID taskId;

    /** Null means "no human actor" - a scheduler or migration wrote the row (V10's comment). */
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "actor_role_code", length = 40)
    private String actorRoleCode;

    @Column(name = "actor_department_id")
    private UUID actorDepartmentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 60)
    private AuditAction action;

    @Column(name = "entity_type", nullable = false, length = 60)
    private String entityType;

    /** Deliberately no FK in the schema: a weak, cross-entity reference. */
    @Column(name = "entity_id")
    private UUID entityId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "old_value")
    private String oldValue;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "new_value")
    private String newValue;

    @Column(name = "reason", length = 2000)
    private String reason;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    // DB default now(); applied before tr_audit_log_chain runs, so it is part of the row hash.
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;
}
