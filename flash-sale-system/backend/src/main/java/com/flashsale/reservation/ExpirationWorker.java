package com.flashsale.reservation;

import com.flashsale.inventory.InventoryBalanceRepository;
import com.flashsale.messaging.OutboxEvent;
import com.flashsale.messaging.OutboxEventRepository;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.Arrays;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class ExpirationWorker {
    private final ReservationRepository reservationRepository;
    private final InventoryBalanceRepository inventoryBalanceRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final org.springframework.data.redis.core.StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> releaseStockScript;
    private final ObjectMapper objectMapper;

    public ExpirationWorker(ReservationRepository reservationRepository,
                            InventoryBalanceRepository inventoryBalanceRepository,
                            OutboxEventRepository outboxEventRepository,
                            org.springframework.data.redis.core.StringRedisTemplate redisTemplate,
                            DefaultRedisScript<Long> releaseStockScript,
                            ObjectMapper objectMapper) {
        this.reservationRepository = reservationRepository;
        this.inventoryBalanceRepository = inventoryBalanceRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.redisTemplate = redisTemplate;
        this.releaseStockScript = releaseStockScript;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void expireReservations() {
        List<Reservation> expired = reservationRepository.findByStatusAndExpiresAtBefore("RESERVED", OffsetDateTime.now());
        for (Reservation res : expired) {
            int updated = reservationRepository.updateStatus(res.getId(), "RESERVED", "EXPIRED");
            if (updated > 0) {
                inventoryBalanceRepository.releaseStock(res.getSaleId(), res.getQuantity());
                
                OutboxEvent event = new OutboxEvent();
                event.setId(UUID.randomUUID().toString());
                event.setEventType("STOCK_RELEASE");
                event.setAggregateId(res.getId());
                event.setPayload("{\"reservationId\":\"" + res.getId() + "\",\"saleId\":" + res.getSaleId() + ",\"quantity\":" + res.getQuantity() + "}");
                event.setCreatedAt(OffsetDateTime.now());
                outboxEventRepository.save(event);
            }
        }
    }

    public void processStockRelease(String payload) throws Exception {
        var root = objectMapper.readTree(payload);
        String reservationId = root.get("reservationId").asText();
        Long saleId = root.get("saleId").asLong();
        int quantity = root.get("quantity").asInt();

        String saleKey = "flashsale:" + saleId;
        String releaseKey = "release:" + reservationId;
        Long result = redisTemplate.execute(releaseStockScript, Arrays.asList(saleKey, releaseKey), String.valueOf(quantity));
        if (result == null || result < 0) {
            if (result != null && result == -1) {
                return; // ALREADY_RELEASED
            }
            throw new RuntimeException("Redis release failed: " + result);
        }
    }
}
