package uz.ithunter.crm.user;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RolePermissionRepository extends JpaRepository<RolePermission, UUID> {

    List<RolePermission> findByRole(Role role);

    @Query("select rp.permission.code from RolePermission rp where rp.role in :roles")
    Set<String> findPermissionCodesByRoleIn(@Param("roles") Set<Role> roles);
}
