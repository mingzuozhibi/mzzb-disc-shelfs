package mingzuozhibi.discshelfs;

import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import mingzuozhibi.common.InetAddressHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Slf4j
@Component
public class AppStartupRunner implements CommandLineRunner {

    @Autowired
    private JmsTemplate template;

    @Value("${spring.application.name}")
    private String moduleName;

    @Value("${server.port}")
    private int port;

    public void run(String... args) throws Exception {
        Optional<String> hostAddress = InetAddressHelper.getHostAddress();
        if (hostAddress.isPresent()) {
            JsonObject root = new JsonObject();
            root.addProperty("name", moduleName);
            root.addProperty("addr", hostAddress.get() + ":" + port);
            jmsSend("module.connect", root.toString());
        } else {
            JsonObject root = new JsonObject();
            root.addProperty("name", moduleName);
            root.addProperty("warn", "Can't get network address");
            jmsSend("module.message", root.toString());
        }
    }

    private void jmsSend(String destinationName, String message) {
        template.convertAndSend(destinationName, message);
        log.info("JMS -> {} [{}]", destinationName, message);
    }

}
