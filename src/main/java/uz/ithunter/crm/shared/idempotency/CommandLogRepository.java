package uz.ithunter.crm.shared.idempotency;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommandLogRepository extends JpaRepository<CommandLog, UUID> {

    Optional<CommandLog> findByIdempotencyKey(String idempotencyKey);
}
