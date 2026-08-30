package uz.ithunter.crm.document;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "document_version")
@Getter
@Setter
@NoArgsConstructor
public class DocumentVersion {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @Column(name = "version_no", nullable = false)
    private int versionNo;

    @Column(name = "content_ref", nullable = false, length = 500)
    private String contentRef;

    // char(64) in the DB (bpchar/JDBC CHAR) - a plain String column mapping is validated as
    // VARCHAR and fails ddl-auto=validate at context startup, same trap as PriceRule/Contract's
    // currency column elsewhere in this codebase.
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.CHAR)
    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Column(name = "file_name", length = 255)
    private String fileName;

    @Column(name = "mime_type", length = 120)
    private String mimeType;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "fields", nullable = false, columnDefinition = "jsonb")
    private String fields = "{}";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private DocumentVersionStatus status = DocumentVersionStatus.DRAFT;

    @Column(name = "created_by_id", nullable = false)
    private UUID createdById;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "supersedes_id")
    private UUID supersedesId;

    @Column(name = "revision_reason", length = 2000)
    private String revisionReason;

    @Column(name = "signed_by_id")
    private UUID signedById;

    @Column(name = "signed_at")
    private Instant signedAt;
}
