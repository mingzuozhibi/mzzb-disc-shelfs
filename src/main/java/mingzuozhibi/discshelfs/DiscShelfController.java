package mingzuozhibi.discshelfs;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
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
public class DiscShelfController {

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

    private String objectResult(JsonElement date, JsonElement page) {
        JsonObject root = new JsonObject();
        root.addProperty("success", true);
        root.add("data", date);
        root.add("page", page);
        return root.toString();
    }

    private JsonElement buildPage(Page<?> page) {
        JsonObject object = new JsonObject();
        object.addProperty("pageSize", page.getNumberOfElements());
        object.addProperty("currentPage", page.getNumber() + 1);
        object.addProperty("totalElements", page.getTotalElements());
        return object;
    }

}
