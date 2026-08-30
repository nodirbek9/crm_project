package uz.ithunter.crm.shared.idempotency;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Maps to {@code command_log} (V11). Closes the one idempotency gap domain-level state checks and
 * unique constraints do not already cover: a retried or double-clicked mutating request replaying
 * the exact same command. See {@link IdempotencyFilter} for the read/claim contract documented in
 * V11's own header comment.
 *
 * <p>{@code requestHash} is {@code char(64)} (a hex SHA-256 digest) - {@code @JdbcTypeCode(CHAR)} is
 * required or a plain {@code String} validates as {@code VARCHAR} and fails
 * {@code ddl-auto=validate} at startup (the same recurring bug documented against
 * {@code AuditLog.prevHash}/{@code rowHash} and Phase 11's {@code PerformedWork}).
 */
@Entity
@Table(name = "command_log")
@Getter
@Setter
@NoArgsConstructor
public class CommandLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "idempotency_key", nullable = false, length = 120)
    private String idempotencyKey;

    @Column(name = "endpoint", nullable = false, length = 200)
    private String endpoint;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "case_id")
    private UUID caseId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "response_status")
    private Integer responseStatus;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_body")
    private String responseBody;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;
}
