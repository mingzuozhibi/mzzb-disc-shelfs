package mingzuozhibi.discshelfs;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import mingzuozhibi.common.gson.GsonFactory;
import mingzuozhibi.common.jms.JmsMessage;
import mingzuozhibi.common.model.Result;
import mingzuozhibi.common.spider.SpiderJsoup;
import mingzuozhibi.common.spider.SpiderRecorder;
import mingzuozhibi.discshelfs.DiscShelfTaskBuilder.DiscShelfTask;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static mingzuozhibi.common.spider.SpiderRecorder.writeContent;
import static mingzuozhibi.discshelfs.DiscShelfTaskBuilder.buildTasks;

@Slf4j
@Service
public class DiscShelfSpider {

    @Autowired
    private JmsMessage jmsMessage;

    @Autowired
    private DiscShelfRepository discShelfRepository;

    private Pattern pattern = Pattern.compile("/dp/([A-Z0-9]+)/");

    public void testFetch() {
        fetchFromAmazon(buildTasks().subList(0, 1));
    }

    public void fetchFromAmazon() {
        fetchFromAmazon(buildTasks());
    }

    public void fetchFromAmazon(List<DiscShelfTask> tasks) {
        SpiderRecorder recorder = new SpiderRecorder("上架信息", tasks.size(), jmsMessage);
        recorder.jmsStartUpdate();

        String cookie = readCookie();
        Gson gson = GsonFactory.createGson();

        for (DiscShelfTask task : tasks) {
            if (recorder.checkBreakCount(5))
                break;
            recorder.jmsStartUpdateRow(task.getOrigin());
            Result<String> bodyResult = SpiderJsoup.waitRequest(task.getUrl(), connection -> {
                connection.header("cookie", cookie);
            });

            if (recorder.checkUnfinished(task.getOrigin(), bodyResult)) {
                continue;
            }
            String content = bodyResult.getContent();
            try {
                List<DiscShelf> result = new LinkedList<>();
                int divCount = 0;

                for (String text : content.split(Pattern.quote("\n&&&\n"))) {
                    if (text.contains(":search-result-")) {
                        String jsonText = text.substring(text.indexOf('{'), text.lastIndexOf('}') + 1);
                        JsonObject json = gson.fromJson(jsonText, JsonObject.class);
                        String html = json.get("html").getAsString();
                        Document document = Jsoup.parseBodyFragment(html);
                        result.addAll(parseDataAsinDiv(document));
                        divCount++;
                    }
                }

                if (divCount > 0) {
                    recorder.jmsSuccessRow(task.getOrigin(), String.format("找到%d条数据", divCount));
                    result.forEach(discShelf -> {
                        if (discShelfRepository.saveOrUpdate(discShelf)) {
                            recorder.jmsFoundData(String.format("发现新碟片[%s]", discShelf));
                        }
                    });
                } else {
                    recorder.jmsFailedRow(task.getOrigin(), "页面数据不符合格式，或者登入已失效");
                    writeContent(content, task.getOrigin());
                }

            } catch (RuntimeException e) {
                recorder.jmsErrorRow(task.getOrigin(), e);
                writeContent(content, task.getOrigin());
                log.warn("捕获异常", e);
            }
        }

        recorder.jmsSummary();
        recorder.jmsEndUpdate();
        jmsMessage.success("本次发现新碟片%d个", recorder.getDataCount());
    }

    private List<DiscShelf> parseDataAsinDiv(Element element) {
        List<DiscShelf> results = new LinkedList<>();
        element.select(".a-size-base.a-link-normal.a-text-bold").forEach(e -> {
            Matcher matcher = pattern.matcher(e.attr("href"));
            if (matcher.find()) {
                String asin = matcher.group(1);
                String type = e.text().trim();
                String title = element.select(".a-color-base.a-text-normal").first().text().trim();
                results.add(new DiscShelf(asin, type, title));
            }
        });
        return results;
    }

    private String readCookie() {
        try {
            return Files.readAllLines(new File("amazon", "cookie").toPath()).get(0);
        } catch (IOException e) {
            jmsMessage.danger("未能读取到cookie");
            throw new RuntimeException();
        }
    }

}
