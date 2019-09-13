package mingzuozhibi.discshelfs;

import mingzuozhibi.common.jms.JmsMessage;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class DiscShelfService {

    @Autowired
    private DiscShelfRepository discShelfRepository;

    @Autowired
    private JmsMessage jmsMessage;

    @Transactional
    public void saveOrUpdate(String asin, String title) {
        if (StringUtils.isNotEmpty(asin)) {
            Optional<DiscShelf> optionalDiscShelf = discShelfRepository.findByAsin(asin);
            if (!optionalDiscShelf.isPresent()) {
                jmsMessage.success(String.format("发现新碟片[Asin=%s][Title=%s]", asin, title));
                discShelfRepository.save(new DiscShelf(asin, title));
                return;
            }
            DiscShelf discShelf = optionalDiscShelf.get();
            if (!discShelf.getTitle().equals(title)) {
                discShelf.setTitle(title);
            }
        }
    }

}
