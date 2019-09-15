package mingzuozhibi.discshelfs;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface DiscShelfRepository extends JpaRepository<DiscShelf, Long> {

    Optional<DiscShelf> findByAsin(String asin);

    boolean existsByAsin(String asin);

    @Transactional
    default void saveOrUpdate(DiscShelf discShelf) {
        Optional<DiscShelf> discShelfOptional = this.findByAsin(discShelf.getAsin());
        if (discShelfOptional.isPresent()) {
            discShelfOptional.get().setTitle(discShelf.getTitle());
        } else {
            this.save(discShelf);
        }
    }

}
