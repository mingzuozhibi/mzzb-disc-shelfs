package mingzuozhibi.discshelfs;

import io.webfolder.cdp.session.SessionFactory;
import lombok.extern.slf4j.Slf4j;
import mingzuozhibi.common.jms.JmsMessage;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static mingzuozhibi.common.ChromeHelper.*;

@Slf4j
@Service
public class DiscShelfSpider {

    @Autowired
    private DiscShelfService discShelfService;
    @Autowired
    private JmsMessage jmsMessage;

    private AtomicInteger errorCount = new AtomicInteger(0);

    public void fetchFromAmazon() {
        jmsMessage.info("扫描新碟片：准备开始");
        errorCount.set(0);

        List<String> taskUrls = DiscShelfSpiderSupport.buildTaskUrls();

        int taskCount = taskUrls.size();
        jmsMessage.info(String.format("扫描新碟片：共%d个任务", taskCount));

        doInSessionFactory(factory -> {
            for (int i = 0; i < taskCount; i++) {
                String taskUrl = taskUrls.get(i);

                jmsMessage.info(String.format("正在抓取页面(%d/%d)：%s", i + 1, taskCount, taskUrl));

                fetchPageFromAmazon(factory, taskUrl);

                if (errorCount.get() >= 5) {
                    jmsMessage.info("扫描新碟片：连续5次数据异常");
                    return;
                }

                threadSleep(30);
            }
        });
        if (errorCount.get() >= 5) {
            jmsMessage.info("扫描新碟片：错误终止");
        } else {
            jmsMessage.info("扫描新碟片：正常完成");
        }
    }

    public void fetchPageFromAmazon(SessionFactory factory, String pageUrl) {
        Document document = null;
        try {
            document = waitRequest(factory, pageUrl);

            Elements newResult = document.select(".s-result-list.sg-row > div[data-asin]");
            Elements oldResult = document.select("#s-results-list-atf > li[data-result-rank]");

            if (newResult.size() > 0) {
                handleNewTypeData(newResult);
            } else if (oldResult.size() > 0) {
                handleOldTypeData(oldResult);
            } else {
                handleErrorData(document);
            }
        } catch (RuntimeException e) {
            handleException(document, e);
        }
    }

    private void handleNewTypeData(Elements newResult) {
        List<Element> results = new ArrayList<>(newResult);
        jmsMessage.info(String.format("解析到新版数据(发现%d条数据)", results.size()));

        results.forEach(element -> {
            String asin = element.attr("data-asin");
            String title = element.select(".a-size-medium.a-color-base.a-text-normal")
                    .first()
                    .text();
            discShelfService.saveOrUpdate(asin, title);
        });
        errorCount.set(0);
    }

    private void handleOldTypeData(Elements oldResult) {
        List<Element> results = oldResult.stream()
                .filter(element -> element.select(".s-sponsored-info-icon").size() == 0)
                .collect(Collectors.toList());
        jmsMessage.info(String.format("解析到旧版数据(发现%d条数据)", results.size()));

        results.forEach(element -> {
            String asin = element.attr("data-asin");
            String title = element.select("a.a-link-normal[title]").stream()
                    .map(e -> e.attr("title"))
                    .collect(Collectors.joining(" "));
            discShelfService.saveOrUpdate(asin, title);
        });
        errorCount.set(0);
    }

    private void handleErrorData(Document document) {
        String outerHtml = document.outerHtml();
        String path = writeToTempFile(outerHtml);
        if (outerHtml.contains("api-services-support@amazon.com")) {
            jmsMessage.warning(String.format("扫描新碟片：已发现反爬虫系统[file=%s]", path));
        } else {
            jmsMessage.warning(String.format("扫描新碟片：未找到数据的页面[file=%s]", path));
        }
        errorCount.incrementAndGet();
    }

    private void handleException(Document document, RuntimeException e) {
        if (document != null) {
            String outerHtml = document.outerHtml();
            String path = writeToTempFile(outerHtml);
            jmsMessage.warning(String.format("扫描新碟片：发生了异常的页面[file=%s]", path));
        } else {
            jmsMessage.warning(String.format("扫描新碟片：未能成功抓取页面[error=%s]", e.getMessage()));
        }
        log.warn("扫描新碟片：导致的异常信息为：", e);
        errorCount.incrementAndGet();
    }

    private String writeToTempFile(String content) {
        try {
            File file = File.createTempFile("DiscShelfSpider", ".html");
            try (BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(file))) {
                bufferedWriter.write(content);
                bufferedWriter.flush();
                return file.getAbsolutePath();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "null";
    }

}
