package mingzuozhibi.discshelfs;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DiscShelfRepository extends JpaRepository<DiscShelf, Long> {

    Optional<DiscShelf> findByAsin(String asin);

}
