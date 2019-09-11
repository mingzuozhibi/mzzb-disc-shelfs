package mingzuozhibi.discshelfs;

import io.webfolder.cdp.session.SessionFactory;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static mingzuozhibi.common.ChromeHelper.*;

@Slf4j
@Service
public class DiscShelfSpider {

    @Autowired
    private DiscShelfRepository discShelfRepository;

    @Autowired
    private JmsTemplate jmsTemplate;

    private AtomicBoolean isBreak = new AtomicBoolean(false);
    private AtomicInteger noError = new AtomicInteger(0);

    private void infoSpiderStatus(String message) {
        log.info(message);
        jmsTemplate.convertAndSend("mzzb-disc-shelfs-status", message);
    }

    private void warnSpiderStatus(String message) {
        log.warn(message);
        jmsTemplate.convertAndSend("mzzb-disc-shelfs-status", message);
        jmsTemplate.convertAndSend("mzzb-disc-shelfs-errors", message);
    }

    public void fetchFromAmazon() {
        infoSpiderStatus("扫描新碟片：准备开始");
        doInSessionFactory(factory -> {
            fetchPage(factory, "深夜动画", 60, "https://www.amazon.co.jp/s?i=dvd&rh=n%3A561958%2Cn%3A562002%2Cn%3A562020&s=date-desc-rank&language=ja_JP&ref=sr_pg_1");
            fetchPage(factory, "日间动画", 10, "https://www.amazon.co.jp/s?i=dvd&rh=n%3A561958%2Cn%3A%21562002%2Cn%3A562026%2Cn%3A2201429051&s=date-desc-rank&language=ja_JP&ref=sr_pg_1");
        });
        if (isBreak.get()) {
            infoSpiderStatus("扫描新碟片：错误终止");
        } else {
            infoSpiderStatus("扫描新碟片：正常完成");
        }
    }

    private void fetchPage(SessionFactory factory, String taskName, int maxPage, String baseUrl) {
        infoSpiderStatus(String.format("开始扫描%s：共%d个", taskName, maxPage));
        for (int page = 1; page <= maxPage && !isBreak.get(); page++) {
            infoSpiderStatus(String.format("正在扫描%s：抓取中(%d/%d)", taskName, page, maxPage));
            String pageUrl = baseUrl + "&page=" + page;

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

            if (noError.get() > 10) {
                isBreak.set(true);
                warnSpiderStatus("扫描新碟片：连续十次数据异常");
                return;
            }

            threadSleep(30);
        }
    }

    private void handleNewTypeData(Elements newResult) {
        List<Element> results = new ArrayList<>(newResult);
        infoSpiderStatus(String.format("解析到新版数据(发现%d条数据)", results.size()));

        results.forEach(element -> {
            String asin = element.attr("data-asin");
            String title = element.select(".a-size-medium.a-color-base.a-text-normal")
                    .first()
                    .text();
            discShelfRepository.saveOrUpdate(asin, title);
        });
        noError.set(0);
    }

    private void handleOldTypeData(Elements oldResult) {
        List<Element> results = oldResult.stream()
                .filter(element -> element.select(".s-sponsored-info-icon").size() == 0)
                .collect(Collectors.toList());
        infoSpiderStatus(String.format("解析到旧版数据(发现%d条数据)", results.size()));

        results.forEach(element -> {
            String asin = element.attr("data-asin");
            String title = element.select("a.a-link-normal[title]").stream()
                    .map(e -> e.attr("title"))
                    .collect(Collectors.joining(" "));
            discShelfRepository.saveOrUpdate(asin, title);
        });
        noError.set(0);
    }

    private void handleErrorData(Document document) {
        String outerHtml = document.outerHtml();
        String path = writeToTempFile(outerHtml);
        if (outerHtml.contains("api-services-support@amazon.com")) {
            warnSpiderStatus(String.format("扫描新碟片：已发现反爬虫系统[file=%s]", path));
            isBreak.set(true);
        } else {
            warnSpiderStatus(String.format("扫描新碟片：未找到数据的页面[file=%s]", path));
            noError.incrementAndGet();
        }
    }

    private void handleException(Document document, RuntimeException e) {
        if (document != null) {
            String outerHtml = document.outerHtml();
            String path = writeToTempFile(outerHtml);
            warnSpiderStatus(String.format("扫描新碟片：发生了异常的页面[file=%s]", path));
        } else {
            warnSpiderStatus(String.format("扫描新碟片：未能成功抓取页面[error=%s]", e.getMessage()));
        }
        log.warn("扫描新碟片：导致的异常信息为：", e);
        noError.incrementAndGet();
    }

    private String writeToTempFile(String content) {
        try {
            File file = File.createTempFile("DiscShelfSpider", "html");
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
