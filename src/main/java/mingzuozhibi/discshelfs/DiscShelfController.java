package mingzuozhibi.discshelfs;

import mingzuozhibi.common.BaseController;
import mingzuozhibi.common.jms.JmsMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Order;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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

    @GetMapping("/discShelfs")
    public String findAll(@RequestParam(defaultValue = "1") int page,
                          @RequestParam(defaultValue = "20") int pageSize) {
        PageRequest pageRequest = PageRequest.of(page - 1, pageSize, Sort.by(Order.desc("id")));
        Page<DiscShelf> resultPage = discShelfRepository.findAll(pageRequest);
        List<DiscShelf> resultList = resultPage.getContent();
        return objectResult(resultList, resultPage);
    }

}
