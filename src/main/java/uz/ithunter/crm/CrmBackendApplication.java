package uz.ithunter.crm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/** {@code @EnableScheduling} is for {@link uz.ithunter.crm.finance.PaymentWaitingScheduler} (Phase 8, spec 12.9). */
@SpringBootApplication
@EnableScheduling
public class CrmBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(CrmBackendApplication.class, args);
    }

}
