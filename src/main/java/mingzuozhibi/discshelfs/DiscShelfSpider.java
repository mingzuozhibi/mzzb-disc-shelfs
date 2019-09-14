package mingzuozhibi.discshelfs;

import lombok.extern.slf4j.Slf4j;
import mingzuozhibi.common.jms.JmsMessage;
import mingzuozhibi.common.model.Result;
import mingzuozhibi.common.spider.SpiderRecorder;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static mingzuozhibi.common.spider.SpiderCdp4j.*;
import static mingzuozhibi.common.spider.SpiderRecorder.writeContent;
import static mingzuozhibi.discshelfs.DiscShelfSpiderSupport.buildTaskUrls;

@Slf4j
@Service
public class DiscShelfSpider {

    private static class SpiderContext {
        private AtomicInteger errorCount = new AtomicInteger(0);
        private AtomicInteger fetchCount = new AtomicInteger(0);
        private AtomicInteger doneCount = new AtomicInteger(0);
        private AtomicInteger discCount = new AtomicInteger(0);
    }

    @Autowired
    private JmsMessage jmsMessage;
    @Autowired
    private DiscShelfRepository discShelfRepository;

    public void fetchFromAmazon() {
        List<String> taskUrls = buildTaskUrls();
        SpiderRecorder recorder = new SpiderRecorder("上架信息", taskUrls.size(), jmsMessage);
        AtomicInteger rowCount = new AtomicInteger(0);
        recorder.jmsStartUpdate();

        doInSessionFactory(factory -> {
            for (String taskUrl : taskUrls) {
                if (recorder.checkBreakCount(5)) break;
                recorder.jmsStartUpdateRow("*");

                String origin = "page=" + recorder.getFetchCount();
                Result<String> bodyResult = waitResult(factory, taskUrl);
                if (recorder.checkUnfinished(bodyResult, origin)) {
                    continue;
                }

                String content = bodyResult.getContent();
                try {
                    Document document = Jsoup.parseBodyFragment(content);
                    Elements elements = document.select(".s-result-list.sg-row > div[data-asin]");
                    if (elements.size() > 0) {
                        recorder.jmsSuccessRow("Found " + elements.size() + " data");
                        parseDiscShelfs(elements, rowCount);
                    } else {
                        if (content.contains("api-services-support@amazon.com")) {
                            recorder.jmsFailedRow("There seems to be an anti-robot system");
                        } else {
                            recorder.jmsFailedRow("Data does not match the format");
                        }
                    }

                } catch (RuntimeException e) {
                    recorder.jmsErrorRow(e);
                    writeContent(content, "page:" + recorder.getFetchCount());
                }

                threadSleep(5);
            }
        });

        jmsMessage.info("Found a total of %d new discs", rowCount.get());
        recorder.jmsSummary();
        recorder.jmsEndUpdate();
    }

    private void parseDiscShelfs(Elements elements, AtomicInteger rowCount) {
        elements.forEach(element -> {
            String asin = element.attr("data-asin");
            String title = element.select(".a-size-medium.a-color-base.a-text-normal").first().text();
            if (discShelfRepository.saveOrUpdate(asin, title)) {
                jmsMessage.success(String.format("Find a new disc [asin=%s][title=%s]", asin, title));
                rowCount.incrementAndGet();
            }
        });
    }

}
