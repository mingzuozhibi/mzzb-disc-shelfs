package mingzuozhibi.discshelfs;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

public abstract class DiscShelfTaskBuilder {

    private static final String BASE_URL1 = "https://www.amazon.co.jp/s/query?i=dvd&" +
        "rh=n%3A561958%2Cn%3A562002%2Cn%3A562020&" +
        "s=date-desc-rank&language=ja_JP&ref=sr_pg_1&page=";

    private static final String BASE_URL2 = "https://www.amazon.co.jp/s/query?i=dvd&" +
        "rh=n%3A561958%2Cn%3A%21562002%2Cn%3A562026%2Cn%3A2201429051&" +
        "s=date-desc-rank&language=ja_JP&ref=sr_pg_1&page=";

    public static List<DiscShelfTask> buildTasks() {
        List<DiscShelfTask> tasks = new ArrayList<>(70);
        for (int page = 1; page <= 60; page++) {
            tasks.add(new DiscShelfTask("深夜动画" + page, BASE_URL1 + page));
        }
        for (int page = 1; page <= 10; page++) {
            tasks.add(new DiscShelfTask("家庭动画" + page, BASE_URL2 + page));
        }
        return tasks;
    }

    @Getter
    @AllArgsConstructor
    public static class DiscShelfTask {
        private String origin;
        private String url;
    }

}
