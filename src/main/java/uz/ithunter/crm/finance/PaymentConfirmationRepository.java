package uz.ithunter.crm.finance;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentConfirmationRepository extends JpaRepository<PaymentConfirmation, UUID> {

    List<PaymentConfirmation> findByPaymentIdOrderByConfirmedAtAsc(UUID paymentId);

    Optional<PaymentConfirmation> findByPaymentIdAndExternalReference(UUID paymentId, String externalReference);
}
