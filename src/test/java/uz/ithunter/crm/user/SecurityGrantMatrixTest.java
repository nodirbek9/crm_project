package uz.ithunter.crm.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import uz.ithunter.crm.AbstractIntegrationTest;

/**
 * Phase 3 DoD: "the seeded grant matrix matches SECURITY_SPEC.md 3, assert it in a test, do not
 * eyeball it." Expected sets are transcribed directly from the {@code INSERT INTO role_permission}
 * statements in {@code V2__security_and_org.sql} - SECURITY_SPEC.md itself calls that migration
 * "the authoritative version" of the table in its 3.
 */
@SpringBootTest
class SecurityGrantMatrixTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    static List<Object[]> roleGrants() {
        return List.of(
                new Object[] {"APPLICANT", Set.of(
                        "APPLICATION:VIEW", "APPLICATION:CREATE", "CASE:VIEW", "DOCUMENT:VIEW", "APPROVAL:ENDORSE")},
                new Object[] {"ACCOUNTANT", Set.of(
                        "CASE:VIEW", "FINANCE:VIEW", "FINANCE:CREATE", "FINANCE:EDIT", "FINANCE:APPROVE",
                        "APPROVAL:ENDORSE", "DOCUMENT:VIEW", "PERFORMED_WORK:VIEW")},
                new Object[] {"HEAD_OF_CERTIFICATION_BODY", Set.of(
                        "CASE:VIEW", "TASK:VIEW", "DOCUMENT:VIEW", "DOCUMENT:APPROVE", "APPROVAL:ENDORSE",
                        "APPROVAL:APPROVE", "DOCUMENT:SIGN", "PERFORMED_WORK:VIEW", "REPORTING:VIEW", "AUDIT:VIEW")},
                new Object[] {"DEPARTMENT_HEAD", Set.of(
                        "CASE:VIEW", "TASK:VIEW", "TASK:CREATE", "TASK:EDIT", "TASK:APPROVE",
                        "DOCUMENT:VIEW", "DOCUMENT:CREATE", "DOCUMENT:EDIT", "APPROVAL:ENDORSE",
                        "PERFORMED_WORK:VIEW", "REPORTING:VIEW")},
                new Object[] {"SPECIALIST", Set.of(
                        "CASE:VIEW", "TASK:VIEW", "TASK:EDIT", "PRIMARY_CHECK:VIEW", "PRIMARY_CHECK:CREATE",
                        "DOCUMENT:VIEW", "DOCUMENT:CREATE", "DOCUMENT:EDIT", "APPROVAL:ENDORSE")},
                new Object[] {"OPERATOR", Set.of(
                        "CASE:VIEW", "TASK:VIEW", "APPLICATION:VIEW", "REPORTING:VIEW")},
                new Object[] {"ADMIN", Set.of(
                        "USER_ADMIN:VIEW", "USER_ADMIN:CREATE", "USER_ADMIN:EDIT", "USER_ADMIN:BLOCK",
                        "WORKFLOW_CONFIG:VIEW", "WORKFLOW_CONFIG:CREATE", "WORKFLOW_CONFIG:EDIT",
                        "REFERENCE_DATA:VIEW", "REFERENCE_DATA:CREATE", "REFERENCE_DATA:EDIT",
                        "REPORTING:VIEW", "AUDIT:VIEW")});
    }

    @ParameterizedTest(name = "{0} has exactly its seeded grant set")
    @MethodSource("roleGrants")
    void roleHasExactlyItsSeededGrants(String roleCode, Set<String> expectedCodes) {
        List<String> actual = jdbc.queryForList(
                """
                SELECT p.code FROM role_permission rp
                JOIN role r ON r.id = rp.role_id
                JOIN permission p ON p.id = rp.permission_id
                WHERE r.code = ?
                """,
                String.class, roleCode);

        assertThat(actual).containsExactlyInAnyOrderElementsOf(expectedCodes);
    }

    @Test
    void adminHoldsNoCaseTaskFinanceOrSignGrants() {
        Integer count = jdbc.queryForObject(
                """
                SELECT count(*) FROM role_permission rp
                JOIN role r ON r.id = rp.role_id
                JOIN permission p ON p.id = rp.permission_id
                WHERE r.code = 'ADMIN' AND (p.section IN ('CASE','TASK','FINANCE') OR p.code = 'DOCUMENT:SIGN')
                """,
                Integer.class);
        assertThat(count).isZero();
    }

    @Test
    void documentSignExistsOnExactlyOneRole() {
        Integer count = jdbc.queryForObject(
                """
                SELECT count(*) FROM role_permission rp
                JOIN permission p ON p.id = rp.permission_id
                WHERE p.code = 'DOCUMENT:SIGN'
                """,
                Integer.class);
        assertThat(count).isEqualTo(1);

        String roleCode = jdbc.queryForObject(
                """
                SELECT r.code FROM role_permission rp
                JOIN role r ON r.id = rp.role_id
                JOIN permission p ON p.id = rp.permission_id
                WHERE p.code = 'DOCUMENT:SIGN'
                """,
                String.class);
        assertThat(roleCode).isEqualTo("HEAD_OF_CERTIFICATION_BODY");
    }
}
