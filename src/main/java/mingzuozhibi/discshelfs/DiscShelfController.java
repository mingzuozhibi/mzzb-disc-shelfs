package mingzuozhibi.discshelfs;

import lombok.SneakyThrows;
import mingzuozhibi.common.BaseController;
import mingzuozhibi.common.jms.JmsMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Order;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.util.List;

import static mingzuozhibi.common.util.ThreadUtils.runWithDaemon;

@RestController
public class DiscShelfController extends BaseController {

    @Autowired
    private JmsMessage jmsMessage;

    @Autowired
    private DiscShelfSpider discShelfSpider;

    @Autowired
    private DiscShelfRepository discShelfRepository;

    @Scheduled(cron = "0 2 3/4 * * ?")
    @GetMapping("/startUpdate")
    public void startUpdate() {
        runWithDaemon(jmsMessage, "startUpdate", () -> {
            discShelfSpider.fetchFromAmazon();
        });
    }

    @GetMapping("/testUpdate")
    public void testUpdate() {
        runWithDaemon(jmsMessage, "testUpdate", () -> {
            discShelfSpider.testFetch();
        });
    }

    @GetMapping("/discShelfs")
    public String findAll(@RequestParam(defaultValue = "1") int page,
                          @RequestParam(defaultValue = "20") int pageSize) {
        PageRequest pageRequest = PageRequest.of(page - 1, pageSize, Sort.by(Order.desc("id")));
        Page<DiscShelf> resultPage = discShelfRepository.findAll(pageRequest);
        List<DiscShelf> resultList = resultPage.getContent();
        return objectResult(resultList, resultPage);
    }

    @JmsListener(destination = "cookie.update")
    public void cookieUpdate(String cookie) {
        try (PrintWriter writer = new PrintWriter(new File("amazon", "cookie"))) {
            writer.println(cookie);
            writer.flush();
            jmsMessage.success("cookie 已更新");
        } catch (Exception e) {
            jmsMessage.warning("cookie 更新失败: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

}
