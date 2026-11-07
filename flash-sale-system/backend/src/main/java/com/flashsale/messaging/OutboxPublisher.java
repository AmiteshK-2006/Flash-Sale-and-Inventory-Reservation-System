package com.flashsale.messaging;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

@Component
public class OutboxPublisher {
    private final OutboxEventRepository repository;
    private final StringRedisTemplate redisTemplate;

    public OutboxPublisher(OutboxEventRepository repository, StringRedisTemplate redisTemplate) {
        this.repository = repository;
        this.redisTemplate = redisTemplate;
    }

    @Scheduled(fixedDelay = 1000)
    @Transactional
    public void publishEvents() {
        List<OutboxEvent> events = repository.findByPublishedAtIsNullOrderByCreatedAtAsc();
        for (OutboxEvent event : events) {
            try {
                Map<String, String> message = new HashMap<>();
                message.put("id", event.getId());
                message.put("type", event.getEventType());
                message.put("payload", event.getPayload());
                redisTemplate.opsForStream().add("flashsale:events", message);
                
                event.setPublishedAt(OffsetDateTime.now());
                repository.save(event);
            } catch (Exception e) {
                event.setRetryCount(event.getRetryCount() + 1);
                event.setLastError(e.getMessage());
                repository.save(event);
            }
        }
    }
}
