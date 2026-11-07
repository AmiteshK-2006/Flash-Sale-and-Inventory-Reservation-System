import os

base_pkg = "/Users/amitesh/Documents/antigravity/optimistic-pascal/flash-sale-system/backend/src/main/java/com/flashsale"

def write_file(path, content):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        f.write(content)

java_files = {
    "messaging/OutboxPublisher.java": """package com.flashsale.messaging;

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
""",
    "messaging/StreamConsumer.java": """package com.flashsale.messaging;

import com.flashsale.order.OrderService;
import com.flashsale.reservation.ExpirationWorker;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;
import org.springframework.data.redis.connection.stream.StreamInfo.XInfoGroups;
import java.time.Duration;

@Component
public class StreamConsumer implements InitializingBean {
    private final StringRedisTemplate redisTemplate;
    private final RedisConnectionFactory factory;
    private final OrderService orderService;
    private final ExpirationWorker expirationWorker;

    public StreamConsumer(StringRedisTemplate redisTemplate, RedisConnectionFactory factory,
                          OrderService orderService, ExpirationWorker expirationWorker) {
        this.redisTemplate = redisTemplate;
        this.factory = factory;
        this.orderService = orderService;
        this.expirationWorker = expirationWorker;
    }

    @Override
    public void afterPropertiesSet() {
        try {
            redisTemplate.opsForStream().createGroup("flashsale:events", "backend-group");
        } catch (Exception e) {
            // Group might already exist
        }

        StreamMessageListenerContainer.StreamMessageListenerContainerOptions<String, MapRecord<String, String, String>> options =
                StreamMessageListenerContainer.StreamMessageListenerContainerOptions.builder()
                        .pollTimeout(Duration.ofMillis(100))
                        .build();

        StreamMessageListenerContainer<String, MapRecord<String, String, String>> container =
                StreamMessageListenerContainer.create(factory, options);

        container.receive(Consumer.from("backend-group", "worker-1"),
                StreamOffset.create("flashsale:events", ReadOffset.lastConsumed()),
                message -> {
                    String type = message.getValue().get("type");
                    String payload = message.getValue().get("payload");
                    try {
                        if ("ORDER_CONFIRMATION".equals(type)) {
                            orderService.processOrderConfirmation(payload);
                        } else if ("STOCK_RELEASE".equals(type)) {
                            expirationWorker.processStockRelease(payload);
                        }
                        redisTemplate.opsForStream().acknowledge("backend-group", message);
                    } catch (Exception e) {
                        e.printStackTrace(); // Handle dead letter or retry
                    }
                });
        container.start();
    }
}
""",
    "order/OrderService.java": """package com.flashsale.order;

import com.flashsale.reservation.Reservation;
import com.flashsale.reservation.ReservationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class OrderService {
    private final OrderRepository orderRepository;
    private final ReservationRepository reservationRepository;
    private final ObjectMapper objectMapper;

    public OrderService(OrderRepository orderRepository, ReservationRepository reservationRepository, ObjectMapper objectMapper) {
        this.orderRepository = orderRepository;
        this.reservationRepository = reservationRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void processOrderConfirmation(String payload) throws Exception {
        var root = objectMapper.readTree(payload);
        String reservationId = root.get("reservationId").asText();
        
        var orderOpt = orderRepository.findByReservationId(reservationId);
        if (orderOpt.isPresent()) {
            return; // Already processed
        }

        Reservation res = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new RuntimeException("Reservation not found"));

        if (!"PAYMENT_SUCCEEDED_PENDING_ORDER".equals(res.getStatus())) {
            return; // Invalid state
        }

        Order order = new Order();
        order.setId(UUID.randomUUID().toString());
        order.setReservationId(reservationId);
        order.setUserId(res.getUserId());
        order.setProductId(res.getProductId());
        order.setQuantity(res.getQuantity());
        order.setUnitPrice(res.getUnitPrice());
        order.setTotalAmount(res.getUnitPrice().multiply(java.math.BigDecimal.valueOf(res.getQuantity())));
        order.setStatus("CONFIRMED");
        order.setCreatedAt(OffsetDateTime.now());
        order.setUpdatedAt(OffsetDateTime.now());
        orderRepository.save(order);

        reservationRepository.updateStatus(reservationId, "PAYMENT_SUCCEEDED_PENDING_ORDER", "CONFIRMED");
    }
}
""",
    "reservation/ExpirationWorker.java": """package com.flashsale.reservation;

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
    private final RedisTemplate<String, Object> redisTemplate;
    private final DefaultRedisScript<Long> releaseStockScript;
    private final ObjectMapper objectMapper;

    public ExpirationWorker(ReservationRepository reservationRepository,
                            InventoryBalanceRepository inventoryBalanceRepository,
                            OutboxEventRepository outboxEventRepository,
                            RedisTemplate<String, Object> redisTemplate,
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
                event.setPayload("{\\"reservationId\\":\\"" + res.getId() + "\\",\\"saleId\\":" + res.getSaleId() + ",\\"quantity\\":" + res.getQuantity() + "}");
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
        Object result = redisTemplate.execute(releaseStockScript, Arrays.asList(saleKey, releaseKey), String.valueOf(quantity));
        if (result instanceof java.util.Map) {
            java.util.Map errMap = (java.util.Map) result;
            String err = (String) errMap.get("err");
            if ("ALREADY_RELEASED".equals(err)) {
                return;
            }
            throw new RuntimeException("Redis release failed: " + err);
        }
    }
}
"""
}

for path, content in java_files.items():
    write_file(f"{base_pkg}/{path}", content)
