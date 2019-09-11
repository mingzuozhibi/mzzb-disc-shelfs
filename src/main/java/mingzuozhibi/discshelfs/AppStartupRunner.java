package mingzuozhibi.discshelfs;

import mingzuozhibi.common.InetAddressHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class AppStartupRunner implements CommandLineRunner {

    @Value("${spring.application.name}")
    private String moduleName;

    @Value("${server.port}")
    private int port;

    @Autowired
    private JmsHelper jmsHelper;

    public void run(String... args) throws Exception {
        Optional<String> hostAddress = InetAddressHelper.getHostAddress();
        if (hostAddress.isPresent()) {
            jmsHelper.sendAddr(moduleName, hostAddress.get() + ":" + port);
        } else {
            jmsHelper.sendWarn(moduleName, "Can't get network address");
        }
    }

}
