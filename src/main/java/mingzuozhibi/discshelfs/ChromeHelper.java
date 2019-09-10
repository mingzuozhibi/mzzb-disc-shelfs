package mingzuozhibi.discshelfs;

import io.webfolder.cdp.Launcher;
import io.webfolder.cdp.session.Session;
import io.webfolder.cdp.session.SessionFactory;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Slf4j
public abstract class ChromeHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChromeHelper.class);

    public static void doInSessionFactory(Consumer<SessionFactory> consumer) {
        String uuid = UUID.randomUUID().toString().substring(0, 8);
        List<String> command = new ArrayList<>();
        command.add("--headless");
        command.add("--target.name=" + uuid);
        Launcher launcher = new Launcher();
        try (SessionFactory factory = launcher.launch(command)) {
            consumer.accept(factory);
        } catch (RuntimeException e) {
            log.warn(e.getMessage(), e);
        }
        killChrome(uuid);
    }

    private static void killChrome(String uuid) {
        String format = "ps -x | grep 'target.name=%s' | grep -v grep | awk '{print $1}' | xargs -t kill -9 2>&1";
        String[] cmds = {"/bin/bash", "-l", "-c", String.format(format, uuid)};
        try {
            Process process = Runtime.getRuntime().exec(cmds);
            process.waitFor();
            String output = IOUtils.toString(process.getInputStream(), Charset.defaultCharset());
            LOGGER.info("kill chrome " + uuid + ": " + output);
        } catch (IOException | InterruptedException e) {
            LOGGER.info("kill chrome " + uuid + ": " + e.getMessage(), e);
        }
    }

    public static Document waitRequest(SessionFactory factory, String url) {
        Exception lastThrown = null;
        for (int retry = 0; retry < 3; retry++) {
            try (Session session = factory.create()) {
                session.setUserAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_13_6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/68.0.3440.106 Safari/537.36");
                session.navigate(url);
                session.waitDocumentReady(38000);
                session.wait(2000);
                return Jsoup.parseBodyFragment(session.getOuterHtml("body"));
            } catch (Exception e) {
                lastThrown = e;
            }
        }
        String format = "Chrome: 无法获取网页内容[url=%s][message=%s]";
        String message = String.format(format, url, lastThrown.getMessage());
        throw new RuntimeException(message, lastThrown);
    }

    public static void threadSleep(int timeout) {
        try {
            TimeUnit.SECONDS.sleep(timeout);
        } catch (InterruptedException ignored) {
        }
    }

}
