package mingzuozhibi.discshelfs;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication(scanBasePackages = "mingzuozhibi")
public class MzzbDiscShelfsApplication {
    public static void main(String[] args) {
        SpringApplication.run(MzzbDiscShelfsApplication.class, args);
    }
}
