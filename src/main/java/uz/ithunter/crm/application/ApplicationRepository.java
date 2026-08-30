package uz.ithunter.crm.application;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicationRepository extends JpaRepository<Application, UUID> {

    Page<Application> findByApplicantId(UUID applicantId, Pageable pageable);
}
