package mingzuozhibi.discshelfs;

import io.webfolder.cdp.session.SessionFactory;
import lombok.extern.slf4j.Slf4j;
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
    private DiscShelfRepository discShelfRepository;

    @Autowired
    private JmsHelper jmsHelper;

    private AtomicInteger errorCount = new AtomicInteger(0);

    private static final String BASE_URL1 = "https://www.amazon.co.jp/s?i=dvd&" +
            "rh=n%3A561958%2Cn%3A562002%2Cn%3A562020&" +
            "s=date-desc-rank&language=ja_JP&ref=sr_pg_1&page=";

    private static final String BASE_URL2 = "https://www.amazon.co.jp/s?i=dvd&" +
            "rh=n%3A561958%2Cn%3A%21562002%2Cn%3A562026%2Cn%3A2201429051&" +
            "s=date-desc-rank&language=ja_JP&ref=sr_pg_1&page=";

    public void fetchFromAmazon() {
        jmsHelper.sendInfo("扫描新碟片：准备开始");
        errorCount.set(0);

        List<String> taskUrls = new ArrayList<>(70);
        for (int page = 1; page <= 60; page++) {
            taskUrls.add(BASE_URL1 + page);
        }
        for (int page = 1; page <= 10; page++) {
            taskUrls.add(BASE_URL2 + page);
        }

        int taskCount = taskUrls.size();
        jmsHelper.sendInfo(String.format("扫描新碟片：共%d个任务", taskCount));

        doInSessionFactory(factory -> {
            for (int i = 0; i < taskCount; i++) {
                String taskUrl = taskUrls.get(i);

                jmsHelper.sendInfo(String.format("正在抓取页面(%d/%d)：%s", i + 1, taskCount, taskUrl));

                fetchPageFromAmazon(factory, taskUrl);

                if (errorCount.get() >= 5) {
                    jmsHelper.sendInfo("扫描新碟片：连续5次数据异常");
                    return;
                }

                threadSleep(30);
            }
        });
        if (errorCount.get() >= 5) {
            jmsHelper.sendInfo("扫描新碟片：错误终止");
        } else {
            jmsHelper.sendInfo("扫描新碟片：正常完成");
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
        jmsHelper.sendInfo(String.format("解析到新版数据(发现%d条数据)", results.size()));

        results.forEach(element -> {
            String asin = element.attr("data-asin");
            String title = element.select(".a-size-medium.a-color-base.a-text-normal")
                    .first()
                    .text();
            discShelfRepository.saveOrUpdate(asin, title);
        });
        errorCount.set(0);
    }

    private void handleOldTypeData(Elements oldResult) {
        List<Element> results = oldResult.stream()
                .filter(element -> element.select(".s-sponsored-info-icon").size() == 0)
                .collect(Collectors.toList());
        jmsHelper.sendInfo(String.format("解析到旧版数据(发现%d条数据)", results.size()));

        results.forEach(element -> {
            String asin = element.attr("data-asin");
            String title = element.select("a.a-link-normal[title]").stream()
                    .map(e -> e.attr("title"))
                    .collect(Collectors.joining(" "));
            discShelfRepository.saveOrUpdate(asin, title);
        });
        errorCount.set(0);
    }

    private void handleErrorData(Document document) {
        String outerHtml = document.outerHtml();
        String path = writeToTempFile(outerHtml);
        if (outerHtml.contains("api-services-support@amazon.com")) {
            jmsHelper.sendWarn(String.format("扫描新碟片：已发现反爬虫系统[file=%s]", path));
        } else {
            jmsHelper.sendWarn(String.format("扫描新碟片：未找到数据的页面[file=%s]", path));
        }
        errorCount.incrementAndGet();
    }

    private void handleException(Document document, RuntimeException e) {
        if (document != null) {
            String outerHtml = document.outerHtml();
            String path = writeToTempFile(outerHtml);
            jmsHelper.sendWarn(String.format("扫描新碟片：发生了异常的页面[file=%s]", path));
        } else {
            jmsHelper.sendWarn(String.format("扫描新碟片：未能成功抓取页面[error=%s]", e.getMessage()));
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
