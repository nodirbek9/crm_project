package uz.ithunter.crm.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import uz.ithunter.crm.AbstractIntegrationTest;

import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TEST_MATRIX.md N-01: "Flyway migrates from empty to head on a fresh container, and the JPA
 * schema validates against it (ddl-auto: validate) - catches every entity/migration drift".
 *
 * <p>Flyway applying V1-V11 cleanly is already implied by this class booting at all (Spring runs
 * Flyway before the context is otherwise usable, against the Testcontainers Postgres in
 * {@link AbstractIntegrationTest}). What this class checks on top of that is that the resulting
 * schema actually matches DATABASE_SCHEMA.md: table count, the audit/immutability machinery,
 * the extensions, and the hand-picked set of partial/unique indexes DATABASE_SCHEMA.md calls out
 * as "constraints worth defending in an interview".
 *
 * <p>Every assertion below was first verified by hand against a real local PostgreSQL 16 (V1-V11
 * applied via psql, in order, from an empty database) before being encoded here - see
 * docs-2/IMPLEMENTATION_STATUS.md Phase 2 notes for that session's exact output.
 */
@SpringBootTest
class SchemaIntegrityTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void allThirtySevenTablesFromDatabaseSchemaExist() {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name <> 'flyway_schema_history'",
                Integer.class);
        assertThat(count).isEqualTo(37);
    }

    @Test
    void requiredExtensionsArePresent() {
        List<String> extensions = jdbc.queryForList(
                "SELECT extname FROM pg_extension WHERE extname IN ('pgcrypto','btree_gin') ORDER BY extname",
                String.class);
        assertThat(extensions).containsExactly("btree_gin", "pgcrypto");
    }

    @Test
    void allCustomFunctionsFromMigrationsExist() {
        List<String> functions = jdbc.queryForList(
                "SELECT proname FROM pg_proc p JOIN pg_namespace n ON n.oid = p.pronamespace " +
                        "WHERE n.nspname = 'public' AND proname IN (" +
                        "'set_updated_at','forbid_mutation','document_version_guard'," +
                        "'task_result_guard','performed_work_bracket_guard'," +
                        "'audit_log_chain','verify_audit_chain') ORDER BY proname",
                String.class);
        assertThat(functions).containsExactly(
                "audit_log_chain", "document_version_guard", "forbid_mutation",
                "performed_work_bracket_guard", "set_updated_at", "task_result_guard",
                "verify_audit_chain");
    }

    /**
     * The partial/unique indexes DATABASE_SCHEMA.md's "Uniqueness that encodes a business rule"
     * table calls out by name. Each one is load-bearing for a later-phase test (see the table's
     * right-hand column in DATABASE_SCHEMA.md), so a migration that silently drops one should
     * fail here rather than surface as a mysterious duplicate row three phases from now.
     */
    @Test
    void namedBusinessRuleIndexesExist() {
        List<String> expected = List.of(
                "uq_case_application", "uq_workflow_code_version", "uq_workflow_one_active",
                "uq_case_stage", "uq_task_case_stage", "uq_task_result_version",
                "uq_task_result_live", "uq_document_version", "uq_docver_signed_once",
                "uq_price_calc_one_active", "uq_performed_work_once", "uq_approval_round_one_open",
                "uq_payment_conf_external", "uq_command_log_key", "uq_applicant_pinfl",
                "uq_applicant_tin");

        // Plain "IN (?, ?, ...)" rather than "= ANY(?)": binding a raw Java String[] as a single
        // JDBC parameter does not reliably become a Postgres array across drivers, whereas one
        // placeholder per element is standard JdbcTemplate varargs binding.
        String placeholders = expected.stream().map(e -> "?").collect(Collectors.joining(","));
        List<String> present = jdbc.queryForList(
                "SELECT indexname FROM pg_indexes WHERE schemaname = 'public' AND indexname IN (" + placeholders + ")",
                String.class, expected.toArray());

        assertThat(present).containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    void referenceSeedDataMatchesSpec16_4AndSpec3() {
        // spec 16.4: 13 sections x 7 actions = 91 permissions, seeded by V2.
        Integer permissionCount = jdbc.queryForObject("SELECT count(*) FROM permission", Integer.class);
        assertThat(permissionCount).isEqualTo(91);

        // spec 3.1-3.9: exactly seven system roles - HEAD_OF_CERTIFICATION_BODY and
        // DEPARTMENT_HEAD are deliberately two separate roles (PLAN_REVIEW C2).
        List<String> roleCodes = jdbc.queryForList("SELECT code FROM role ORDER BY code", String.class);
        assertThat(roleCodes).containsExactlyInAnyOrder(
                "ADMIN", "APPLICANT", "ACCOUNTANT", "HEAD_OF_CERTIFICATION_BODY",
                "DEPARTMENT_HEAD", "SPECIALIST", "OPERATOR");
    }

    @Test
    void crmAppRoleHasOnlySelectAndInsertOnAuditLog() {
        List<String> privileges = jdbc.queryForList(
                "SELECT privilege_type FROM information_schema.role_table_grants " +
                        "WHERE table_name = 'audit_log' AND grantee = 'crm_app' ORDER BY privilege_type",
                String.class);
        assertThat(privileges).containsExactly("INSERT", "SELECT");
    }

    /**
     * verify_audit_chain() (DATABASE_SCHEMA.md "Audit strategy", layer 4) must be callable and
     * must report no break on a chain that was only ever appended to - the demo-scenario version
     * of this check is A-05 in TEST_MATRIX.md.
     */
    @Test
    @Transactional
    void verifyAuditChainReportsNoBreakOnAnUntamperedChain() {
        jdbc.update("INSERT INTO audit_log (action, entity_type) VALUES ('USER_CREATED','app_user')");
        jdbc.update("INSERT INTO audit_log (action, entity_type) VALUES ('WORKFLOW_PUBLISHED','workflow')");

        List<Long> brokenSeqs = jdbc.queryForList("SELECT broken_seq FROM verify_audit_chain()", Long.class);

        assertThat(brokenSeqs).isEmpty();
    }

    /**
     * spec 20.3 / A-02, A-03: no user category may modify or delete audit records, and the
     * append-only trigger (tr_audit_log_immutable / tr_audit_log_no_truncate) enforces this even
     * for the schema owner, independent of the crm_app grant restriction checked above.
     */
    // Split into two independent transactions on purpose: once the trigger raises inside a
    // Postgres transaction, that transaction is aborted and every further statement on it fails
    // with a generic "current transaction is aborted" error rather than the trigger's own
    // message - so a second forbidden statement in the *same* transaction would not actually be
    // exercising the trigger a second time. @Transactional rolls each method back independently.

    @Test
    @Transactional
    void auditLogRejectsUpdate() {
        jdbc.update("INSERT INTO audit_log (action, entity_type) VALUES ('USER_CREATED','app_user')");
        // The row's seq is whatever the shared bigserial sequence is currently at (other test
        // methods may have advanced it, even though their inserts rolled back - sequences are
        // not transactional in Postgres) - never assume it is 1, always read it back.
        Long seq = jdbc.queryForObject("SELECT max(seq) FROM audit_log", Long.class);

        assertThatThrownBy(() -> jdbc.update("UPDATE audit_log SET reason = 'tamper' WHERE seq = ?", seq))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("append-only");
    }

    @Test
    @Transactional
    void auditLogRejectsDelete() {
        jdbc.update("INSERT INTO audit_log (action, entity_type) VALUES ('USER_CREATED','app_user')");
        Long seq = jdbc.queryForObject("SELECT max(seq) FROM audit_log", Long.class);

        assertThatThrownBy(() -> jdbc.update("DELETE FROM audit_log WHERE seq = ?", seq))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("append-only");
    }
}
