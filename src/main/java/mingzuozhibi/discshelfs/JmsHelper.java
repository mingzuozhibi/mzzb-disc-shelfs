package mingzuozhibi.discshelfs;

import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Slf4j
@Component
public class JmsHelper {

    @Autowired
    private JmsTemplate template;

    public void sendAddr(String moduleName, String moduleAddr) {
        JsonObject root = new JsonObject();
        root.addProperty("name", moduleName);
        root.addProperty("addr", moduleAddr);
        jmsSend("module.connect", root.toString());
    }

    public void sendInfo(String moduleName, String message) {
        sendModuleMsg(moduleName, "info", message);
    }

    public void sendWarn(String moduleName, String message) {
        sendModuleMsg(moduleName, "warn", message);
    }

    private void sendModuleMsg(String moduleName, String type, String message) {
        JsonObject root = new JsonObject();
        root.addProperty("name", moduleName);
        root.add("data", buildData(type, message));
        jmsSend("module.message", root.toString());
    }

    private JsonObject buildData(String type, String message) {
        JsonObject data = new JsonObject();
        data.addProperty("type", type);
        data.addProperty("text", message);
        data.addProperty("createOn", Instant.now().toEpochMilli());
        return data;
    }

    private void jmsSend(String destinationName, String message) {
        template.convertAndSend(destinationName, message);
        log.info("JMS -> {} {}", destinationName, message);
    }

}
