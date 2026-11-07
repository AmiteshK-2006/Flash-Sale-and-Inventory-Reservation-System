package com.flashsale.messaging;

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
