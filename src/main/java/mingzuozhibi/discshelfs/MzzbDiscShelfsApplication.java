package mingzuozhibi.discshelfs;

import com.google.gson.Gson;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class MzzbDiscShelfsApplication {

    public static void main(String[] args) {
        SpringApplication.run(MzzbDiscShelfsApplication.class, args);
    }

    @Bean
    public Gson gson() {
        return GsonUtils.getGson();
    }

}
