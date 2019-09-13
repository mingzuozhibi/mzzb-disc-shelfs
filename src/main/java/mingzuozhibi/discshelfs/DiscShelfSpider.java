package mingzuozhibi.discshelfs;

import lombok.extern.slf4j.Slf4j;
import mingzuozhibi.common.jms.JmsMessage;
import mingzuozhibi.common.model.Result;
import org.jsoup.Jsoup;
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

import static mingzuozhibi.common.model.Result.formatErrors;
import static mingzuozhibi.common.util.ChromeUtils.*;
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
        jmsMessage.info("扫描新碟片：准备开始");

        List<String> taskUrls = buildTaskUrls();
        int taskCount = taskUrls.size();
        jmsMessage.info(String.format("扫描新碟片：共%d个任务", taskCount));

        SpiderContext context = new SpiderContext();
        doInSessionFactory(factory -> {
            for (String taskUrl : taskUrls) {
                if (checkBreak(context)) return;
                int page = context.fetchCount.incrementAndGet();

                // 抓取中
                jmsMessage.info(String.format("正在抓取页面(%d/%d)：%s", page, taskCount, taskUrl));
                Result<String> bodyResult = waitResult(factory, taskUrl);
                if (bodyResult.notDone()) {
                    jmsMessage.warning("抓取中遇到错误：" + bodyResult.formatError());
                    context.errorCount.incrementAndGet();
                    continue;
                }

                // 解析中
                String outerHtml = bodyResult.getContent();
                try {
                    Document document = Jsoup.parseBodyFragment(outerHtml);
                    Elements elements = document.select(".s-result-list.sg-row > div[data-asin]");

                    if (elements.size() > 0) {
                        handleElements(elements, context);
                    } else {
                        handleNotFound(outerHtml, context);
                    }

                } catch (RuntimeException e) {
                    handleException(outerHtml, e, context);
                }

                threadSleep(15);
            }
        });

        if (context.errorCount.get() >= 5) {
            jmsMessage.info("扫描新碟片：异常终止");
        } else {
            jmsMessage.info("扫描新碟片：正常完成");
        }
    }

    private boolean checkBreak(SpiderContext context) {
        if (context.errorCount.get() >= 5) {
            jmsMessage.info("扫描新碟片：连续5次数据异常");
            return true;
        }
        return false;
    }

    private void handleElements(Elements elements, SpiderContext context) {
        List<Element> results = new ArrayList<>(elements);
        jmsMessage.info(String.format("解析到%d条数据", results.size()));

        results.forEach(element -> {
            String asin = element.attr("data-asin");
            String title = element.select(".a-size-medium.a-color-base.a-text-normal")
                .first()
                .text();
            if (discShelfRepository.saveOrUpdate(asin, title)) {
                context.discCount.incrementAndGet();
                jmsMessage.success(String.format("发现新碟片[Asin=%s][Title=%s]", asin, title));
            }
        });

        context.errorCount.set(0);
        context.doneCount.incrementAndGet();
    }

    private void handleNotFound(String outerHtml, SpiderContext context) {
        if (outerHtml.contains("api-services-support@amazon.com")) {
            jmsMessage.warning("扫描新碟片：已发现反爬虫系统");
        } else {
            jmsMessage.warning("扫描新碟片：未找到符合的数据");
            recordErrorContent("未找到符合的数据", outerHtml);
        }
        context.errorCount.incrementAndGet();
    }

    private void handleException(String outerHtml, RuntimeException e, SpiderContext context) {
        jmsMessage.warning("解析中发生异常：" + formatErrors(e));
        recordErrorContent("解析中发生异常", outerHtml);
        context.errorCount.incrementAndGet();
    }

    private void recordErrorContent(String type, String outerHtml) {
        String path = writeToTempFile(outerHtml);
        log.warn("解析中发生异常：type={} file={}", type, path);
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
