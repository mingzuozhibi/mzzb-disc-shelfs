package mingzuozhibi.discshelfs;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import mingzuozhibi.common.BaseModel;

import javax.persistence.Column;
import javax.persistence.Entity;
import java.time.Instant;

@Entity
@Setter
@Getter
@NoArgsConstructor
public class DiscShelf extends BaseModel {

    @Column(length = 20, nullable = false, unique = true)
    private String asin;

    @Column(length = 500, nullable = false)
    private String title;

    @Column(nullable = false)
    private Instant createOn;

    public DiscShelf(String asin, String title) {
        this.asin = asin;
        this.title = title;
        this.createOn = Instant.now();
    }

    public DiscShelf(String asin, String title, Instant createOn) {
        this.asin = asin;
        this.title = title;
        this.createOn = createOn;
    }

}
