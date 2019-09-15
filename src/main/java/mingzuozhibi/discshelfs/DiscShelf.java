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

    @Column(length = 20)
    private String type;

    @Column(length = 500, nullable = false)
    private String title;

    @Column(nullable = false)
    private Instant createOn;

    public DiscShelf(String asin, String type, String title) {
        this.asin = asin;
        this.type = type;
        this.title = title;
        this.createOn = Instant.now();
    }

    @Override
    public String toString() {
        return String.format("asin='%s', type='%s', title='%s'", asin, type, title);
    }

}
