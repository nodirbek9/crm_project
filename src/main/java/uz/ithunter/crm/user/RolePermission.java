package uz.ithunter.crm.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * An owned join entity rather than a plain {@code @ManyToMany}, per FINAL_DOMAIN_MODEL.md 1.3:
 * "so that grants are auditable" - every insert/delete is meant to raise {@code ROLE_PERMISSION_CHANGED}
 * in the audit log (spec 16.10), which needs a real entity to hang that behaviour off of later.
 */
@Entity
@Table(name = "role_permission")
@Getter
@Setter
@NoArgsConstructor
public class RolePermission {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "permission_id", nullable = false)
    private Permission permission;

    @Column(name = "granted_at", nullable = false, insertable = false, updatable = false)
    private Instant grantedAt;

    @Column(name = "granted_by")
    private UUID grantedBy;
}
