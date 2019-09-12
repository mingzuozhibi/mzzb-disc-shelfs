package mingzuozhibi.discshelfs;

import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Slf4j
@Component
public class JmsHelper {

    @Value("${spring.application.name}")
    private String moduleName;

    @Autowired
    private JmsTemplate template;

    public void doSend(String name, String message) {
        template.convertAndSend(name, message);
    }

    public void sendInfo(String message) {
        String msgData = buildMsgData("info", message).toString();
        template.convertAndSend("module.message", msgData);
        log.info("JMS -> {} {}", "module.message", message);
    }

    public void sendWarn(String message) {
        String msgData = buildMsgData("warn", message).toString();
        template.convertAndSend("module.message", msgData);
        log.warn("JMS -> {} {}", "module.message", message);
    }

    public void sendAddr(String moduleAddr) {
        JsonObject addrData = buildAddrData(moduleAddr);
        template.convertAndSend("module.connect", addrData.toString());
        log.info("JMS -> {} {}", "module.connect", moduleAddr);
    }

    private JsonObject buildMsgData(String type, String text) {
        JsonObject data = new JsonObject();
        data.addProperty("type", type);
        data.addProperty("text", text);
        data.addProperty("createOn", Instant.now().toEpochMilli());

        JsonObject root = new JsonObject();
        root.addProperty("name", moduleName);
        root.add("data", data);
        return root;
    }

    private JsonObject buildAddrData(String moduleAddr) {
        JsonObject root = new JsonObject();
        root.addProperty("name", moduleName);
        root.addProperty("addr", moduleAddr);
        return root;
    }

}
