package mingzuozhibi.discshelfs;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import mingzuozhibi.common.BaseController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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
    private Gson gson;

    @Autowired
    private DiscShelfSpider discShelfSpider;

    @Autowired
    private DiscShelfRepository discShelfRepository;

    @Scheduled(cron = "0 0 5/6 * * ?")
    @GetMapping("/discShelfs/fetch")
    public void startFetch() {
        Thread thread = new Thread(discShelfSpider::fetchFromAmazon);
        thread.setDaemon(true);
        thread.start();
    }

    @GetMapping("/discShelfs")
    public String findAll(@RequestParam(defaultValue = "1") int page,
                          @RequestParam(defaultValue = "20") int pageSize) {
        PageRequest pageRequest = PageRequest.of(page - 1, pageSize, Sort.by(Order.desc("id")));
        Page<DiscShelf> results = discShelfRepository.findAll(pageRequest);
        List<DiscShelf> content = results.getContent();
        return objectResult(gson.toJsonTree(content), buildPage(results));
    }

    public JsonElement buildPage(Page<?> page) {
        JsonObject object = new JsonObject();
        Pageable pageable = page.getPageable();
        object.addProperty("pageSize", pageable.getPageSize());
        object.addProperty("currentPage", pageable.getPageNumber() + 1);
        object.addProperty("totalElements", page.getTotalElements());
        return object;
    }

}
