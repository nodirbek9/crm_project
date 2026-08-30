package uz.ithunter.crm.admin;

import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import uz.ithunter.crm.admin.dto.RolePermissionsResponse;
import uz.ithunter.crm.admin.dto.UpdateRolePermissionsRequest;

/**
 * Demo-profile-only convenience: grants {@code DEPARTMENT_HEAD} the {@code APPLICATION:EDIT}/
 * {@code CASE:EDIT} permissions a real deployment configures once through the admin API
 * (ASSUMPTIONS.md A30/A50) - the specification's own permission table grants these to no role, by
 * design, so a fresh {@code docker compose up} would otherwise stop at the second step of the
 * demo scenario until an admin makes that one API call by hand.
 *
 * <p>Deliberately {@code @Profile("demo")} only, never {@code "test"}: the seeded matrix
 * {@code SecurityGrantMatrixTest} asserts must stay an exact, unmodified transcription of
 * {@code SECURITY_SPEC.md} 3 (V2's migration) - this runner would make that assertion fail if it
 * ran under the "test" profile too, since the demo container shares the same database. Idempotent
 * (goes through {@link AdminRolePermissionService#update}, which is itself a union-and-diff), so
 * it is safe to run on every container restart.
 */
@Component
@Profile("demo")
public class DemoBootstrapRunner implements CommandLineRunner {

    private final AdminRolePermissionService adminRolePermissionService;

    public DemoBootstrapRunner(AdminRolePermissionService adminRolePermissionService) {
        this.adminRolePermissionService = adminRolePermissionService;
    }

    @Override
    public void run(String... args) {
        RolePermissionsResponse current = adminRolePermissionService.get("DEPARTMENT_HEAD");
        Set<String> desired = new LinkedHashSet<>(current.permissionCodes());
        desired.add("APPLICATION:EDIT");
        desired.add("CASE:EDIT");
        adminRolePermissionService.update("DEPARTMENT_HEAD", new UpdateRolePermissionsRequest(desired));
    }
}
