package mingzuozhibi.discshelfs;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface DiscShelfRepository extends JpaRepository<DiscShelf, Long> {

    Optional<DiscShelf> findByAsin(String asin);

    @Transactional
    default void saveOrUpdate(String asin, String title) {
        if (asin != null && asin.length() > 0) {
            Optional<DiscShelf> optionalDiscShelf = findByAsin(asin);
            if (!optionalDiscShelf.isPresent()) {
                save(new DiscShelf(asin, title));
                return;
            }
            DiscShelf discShelf = optionalDiscShelf.get();
            if (!discShelf.getTitle().equals(title)) {
                discShelf.setTitle(title);
            }
        }
    }

}
