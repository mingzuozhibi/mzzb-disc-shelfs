package mingzuozhibi.discshelfs;

import java.util.ArrayList;
import java.util.List;

public abstract class DiscShelfSpiderSupport {

    private static final String BASE_URL1 = "https://www.amazon.co.jp/s?i=dvd&" +
            "rh=n%3A561958%2Cn%3A562002%2Cn%3A562020&" +
            "s=date-desc-rank&language=ja_JP&ref=sr_pg_1&page=";

    private static final String BASE_URL2 = "https://www.amazon.co.jp/s?i=dvd&" +
            "rh=n%3A561958%2Cn%3A%21562002%2Cn%3A562026%2Cn%3A2201429051&" +
            "s=date-desc-rank&language=ja_JP&ref=sr_pg_1&page=";

    public static List<String> buildTaskUrls() {
        List<String> taskUrls = new ArrayList<>(70);
        for (int page = 1; page <= 60; page++) {
            taskUrls.add(BASE_URL1 + page);
        }
        for (int page = 1; page <= 10; page++) {
            taskUrls.add(BASE_URL2 + page);
        }
        return taskUrls;
    }

}
