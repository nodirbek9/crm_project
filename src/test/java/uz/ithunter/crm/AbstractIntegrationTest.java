package uz.ithunter.crm;

import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base class for every integration test (TEST_MATRIX.md layout: {@code src/test/java/.../integration/}
 * and friends all extend this). Boots one shared PostgreSQL 16 Testcontainers instance for the
 * whole suite and wires it into the Spring context via {@link DynamicPropertySource} - no H2
 * anywhere, since several later-phase tests assert on real Postgres constraints/triggers that H2
 * cannot emulate.
 *
 * <p>This is Testcontainers' documented "singleton container" pattern (manual lifecycle control,
 * started once in a static initializer, never stopped explicitly): deliberately NOT
 * {@code @Testcontainers}/{@code @Container}-managed, because that annotation pair ties the
 * container's start/stop to each individual test class's JUnit5 lifecycle - with several test
 * classes now extending this base, that caused the container to be torn down after the first
 * class's tests finished and a brand new one (on a new port) to be started for the next class,
 * which is wasted time at best and a `Connection refused` flake at worst. A single container
 * started once and left running is reaped by Testcontainers' own Ryuk container at JVM exit either
 * way, so nothing here leaks between `mvn verify` runs.
 *
 * <p>Concrete test classes add their own {@code @SpringBootTest} (with whatever
 * {@code webEnvironment} they need) on top of this class.
 */
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("crm_test")
            .withUsername("crm_test")
            .withPassword("crm_test");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
