package mingzuozhibi.discshelfs;

import com.google.gson.Gson;
import mingzuozhibi.common.BaseController;
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

@RestController
public class DiscShelfController extends BaseController {

    @Autowired
    private JmsHelper jmsHelper;

    @Autowired
    private DiscShelfSpider discShelfSpider;

    @Autowired
    private DiscShelfRepository discShelfRepository;

    private Gson gson = GsonUtils.getGson();

    @Scheduled(cron = "0 0 5/6 * * ?")
    @GetMapping("/discShelfs/fetch")
    public void startFetch() {
        runWithDaemon(discShelfSpider::fetchFromAmazon);
    }

    @GetMapping("/discShelfs")
    public String findAll(@RequestParam(defaultValue = "1") int page,
                          @RequestParam(defaultValue = "20") int pageSize) {
        PageRequest pageRequest = PageRequest.of(page - 1, pageSize, Sort.by(Order.desc("id")));
        Page<DiscShelf> results = discShelfRepository.findAll(pageRequest);
        List<DiscShelf> content = results.getContent();
        return objectResult(gson.toJsonTree(content), buildPage(results));
    }

    private void runWithDaemon(Runnable runnable) {
        Thread thread = new Thread(runnable);
        thread.setDaemon(true);
        thread.setUncaughtExceptionHandler((t, e) -> {
            jmsHelper.sendWarn(String.format("Thread %s: Exit: %s %s"
                    , t.getName(), e.getClass().getName(), e.getMessage()));
        });
        thread.start();
    }

}
