package mingzuozhibi.discshelfs;

import lombok.extern.slf4j.Slf4j;
import mingzuozhibi.common.jms.JmsMessage;
import mingzuozhibi.common.model.Result;
import mingzuozhibi.common.spider.SpiderRecorder;
import mingzuozhibi.discshelfs.DiscShelfTaskBuilder.DiscShelfTask;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static mingzuozhibi.common.spider.SpiderCdp4j.*;
import static mingzuozhibi.common.spider.SpiderRecorder.writeContent;
import static mingzuozhibi.discshelfs.DiscShelfTaskBuilder.buildTasks;

@Slf4j
@Service
public class DiscShelfSpider {

    @Autowired
    private JmsMessage jmsMessage;
    @Autowired
    private DiscShelfRepository discShelfRepository;

    private Pattern patternOfAsin = Pattern.compile("/dp/([A-Z0-9]+)/");

    public void fetchFromAmazon() {
        List<DiscShelfTask> tasks = buildTasks();
        SpiderRecorder recorder = new SpiderRecorder("上架信息", tasks.size(), jmsMessage);
        recorder.jmsStartUpdate();

        doInSessionFactory(factory -> {
            for (DiscShelfTask task : tasks) {
                if (recorder.checkBreakCount(5)) break;
                recorder.jmsStartUpdateRow(task.getOrigin());

                Result<String> bodyResult = waitResult(factory, task.getUrl());
                if (recorder.checkUnfinished(task.getOrigin(), bodyResult)) {
                    continue;
                }

                String content = bodyResult.getContent();
                try {
                    List<DiscShelf> discShelfs = parse(content);
                    if (discShelfs.size() > 0) {
                        recorder.jmsSuccessRow(task.getOrigin(), String.format("找到%d条数据", discShelfs.size()));
                        discShelfs.forEach(discShelf -> {
                            if (discShelfRepository.saveOrUpdate(discShelf)) {
                                recorder.jmsFoundData(String.format("发现新碟片[%s]", discShelf));
                            }
                        });
                    } else {
                        if (content.contains("api-services-support@amazon.com")) {
                            recorder.jmsFailedRow(task.getOrigin(), "发现日亚反爬虫系统");
                        } else {
                            recorder.jmsFailedRow(task.getOrigin(), "页面数据不符合格式");
                            writeContent(content, task.getOrigin());
                        }
                    }
                } catch (RuntimeException e) {
                    recorder.jmsErrorRow(task.getOrigin(), e);
                    writeContent(content, task.getOrigin());
                    log.warn("捕获异常", e);
                }
            }
        });

        recorder.jmsSummary();
        recorder.jmsEndUpdate();
        jmsMessage.success("本次发现新碟片%d个", recorder.getDataCount());
    }

    private List<DiscShelf> parse(String content) {
        Document document = Jsoup.parseBodyFragment(content);
        Elements elements = document.select(".s-result-list.sg-row > div[data-asin]");

        List<DiscShelf> discShelfs = new LinkedList<>();
        elements.forEach(element -> {
            String title = element.select(".a-color-base.a-text-normal").first().text();
            element.select(".a-size-base.a-link-normal.a-text-bold").forEach(e -> {
                String type = e.text().trim();
                String href = e.attr("href");
                Matcher matcher = patternOfAsin.matcher(href);
                if (matcher.find()) {
                    String asin = matcher.group(1);
                    discShelfs.add(new DiscShelf(asin, type, title));
                }
            });
        });
        return discShelfs;
    }

}
