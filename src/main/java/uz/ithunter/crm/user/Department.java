package uz.ithunter.crm.user;

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

/**
 * {@code parentId}/{@code headUserId} are kept as raw ids (not JPA relations) per
 * FINAL_DOMAIN_MODEL.md 1.4's own field typing - nothing in this slice needs to navigate the
 * department tree or the head-user object, and a raw id avoids a Department&lt;-&gt;User
 * bidirectional graph for a field that isn't read yet.
 */
@Entity
@Table(name = "department")
@Getter
@Setter
@NoArgsConstructor
public class Department {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "code", nullable = false, unique = true, length = 40)
    private String code;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "parent_id")
    private UUID parentId;

    @Column(name = "head_user_id")
    private UUID headUserId;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    // DB-trigger managed (tr_department_updated -> set_updated_at()); never written by Hibernate.
    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;
}
