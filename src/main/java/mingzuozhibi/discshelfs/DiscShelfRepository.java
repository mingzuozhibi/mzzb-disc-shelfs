package mingzuozhibi.discshelfs;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface DiscShelfRepository extends JpaRepository<DiscShelf, Long> {

    Optional<DiscShelf> findByAsin(String asin);

    @Transactional
    default boolean saveOrUpdate(DiscShelf discShelf) {
        Optional<DiscShelf> discShelfOptional = this.findByAsin(discShelf.getAsin());
        if (discShelfOptional.isPresent()) {
            DiscShelf shelf = discShelfOptional.get();
            shelf.setType(discShelf.getType());
            shelf.setTitle(discShelf.getTitle());
            return false;
        } else {
            this.save(discShelf);
            return true;
        }
    }

}
