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

import java.util.List;

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

    public void fetchFromAmazon() {
        List<DiscShelfTask> tasks = buildTasks();
        SpiderRecorder recorder = new SpiderRecorder("上架信息", tasks.size(), jmsMessage);
        recorder.jmsStartUpdate();

        doInSessionFactory(factory -> {
            for (DiscShelfTask task : tasks) {
                if (recorder.checkBreakCount(5)) break;
                recorder.jmsStartUpdateRow(task.getOrigin());

                Result<String> bodyResult = waitResult(factory, task.getUrl());
                if (recorder.checkUnfinished(bodyResult)) {
                    continue;
                }

                String content = bodyResult.getContent();
                try {
                    Document document = Jsoup.parseBodyFragment(content);
                    Elements elements = document.select(".s-result-list.sg-row > div[data-asin]");
                    if (elements.size() > 0) {
                        recorder.jmsSuccessRow(task.getOrigin(), String.format("找到%d条数据", elements.size()));
                        parseDiscShelfs(elements, recorder);
                    } else {
                        if (content.contains("api-services-support@amazon.com")) {
                            recorder.jmsFailedRow("发现日亚反爬虫系统");
                        } else {
                            recorder.jmsFailedRow("页面数据未通过校验");
                        }
                    }

                } catch (RuntimeException e) {
                    recorder.jmsErrorRow(e);
                    writeContent(content, "page=" + recorder.getFetchCount());
                }

                threadSleep(5);
            }
        });

        jmsMessage.success("本次发现新碟片%个", recorder.getDataCount());
        recorder.jmsSummary();
        recorder.jmsEndUpdate();
    }

    private void parseDiscShelfs(Elements elements, SpiderRecorder recorder) {
        elements.forEach(element -> {
            String asin = element.attr("data-asin");
            String title = element.select(".a-size-medium.a-color-base.a-text-normal").first().text();
            if (discShelfRepository.saveOrUpdate(asin, title)) {
                recorder.jmsFoundData(String.format("发现新碟片[asin=%s][title=%s]", asin, title));
            }
        });
    }

}
