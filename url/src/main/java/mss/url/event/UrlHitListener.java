package mss.url.event;

import java.sql.Timestamp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import mss.url.model.mss_analytics.Raw;
import mss.url.repository.RawRepository;

@Component
public class UrlHitListener {

    private static final Logger log = LoggerFactory.getLogger(UrlHitListener.class);
    private final RawRepository rawRepository;

    public UrlHitListener(RawRepository rawRepository) {
        this.rawRepository = rawRepository;
    }

    @Async
    @EventListener
    public void onHit(UrlHitEvent event) {
        try {
            rawRepository.save(new Raw(event.code(), Timestamp.from(event.hitTime()), event.ipAddress()));
        } catch (Exception e) {
            // Analytics must never break redirects
            log.warn("Failed to record hit for {}: {}", event.code(), e.getMessage());
        }
    }
}
