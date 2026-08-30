package uz.ithunter.crm;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Phase 1 Definition of Done: "AbstractIntegrationTest boots a Testcontainers postgres and passes
 * an empty test." This is that test - the Spring context (with an empty schema beyond V1's
 * extensions/functions) must load cleanly against the containerized database.
 */
@SpringBootTest
class CrmBackendApplicationTests extends AbstractIntegrationTest {

    @Test
    void contextLoads() {
    }

}
