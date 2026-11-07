package com.flashsale;

import com.flashsale.reservation.ReservationService;
import com.flashsale.sale.FlashSale;
import com.flashsale.sale.FlashSaleRepository;
import com.flashsale.product.Product;
import com.flashsale.product.ProductRepository;
import com.flashsale.inventory.InventoryBalance;
import com.flashsale.inventory.InventoryBalanceRepository;
import com.flashsale.auth.UserRepository;
import com.flashsale.auth.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.containers.GenericContainer;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.time.OffsetDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
public class ConcurrencyTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
    }

    @Autowired
    private ReservationService reservationService;
    
    @Autowired
    private FlashSaleRepository flashSaleRepository;
    
    @Autowired
    private ProductRepository productRepository;
    
    @Autowired
    private InventoryBalanceRepository inventoryBalanceRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    public void testHighConcurrencyReservation() throws InterruptedException {
        // Prepare data
        User u = new User();
        u.setName("Test");
        u.setEmail("test@test.com");
        u.setPasswordHash("pass");
        u.setRole("USER");
        u.setCreatedAt(OffsetDateTime.now());
        u = userRepository.save(u);

        Product p = new Product();
        p.setName("Console");
        p.setPrice(BigDecimal.valueOf(49999));
        p.setCreatedAt(OffsetDateTime.now());
        p = productRepository.save(p);

        int totalStock = 100;
        int requests = 10000;

        FlashSale sale = new FlashSale();
        sale.setProductId(p.getId());
        sale.setStartTime(OffsetDateTime.now().minusMinutes(5));
        sale.setEndTime(OffsetDateTime.now().plusMinutes(10));
        sale.setSaleStock(totalStock);
        sale.setMaxQuantityPerPurchase(1);
        sale.setStatus("ACTIVE");
        sale.setCreatedAt(OffsetDateTime.now());
        sale = flashSaleRepository.save(sale);

        InventoryBalance inv = new InventoryBalance();
        inv.setSaleId(sale.getId());
        inv.setTotalStock(totalStock);
        inv.setReservedStock(0);
        inv.setSoldStock(0);
        inv.setUpdatedAt(OffsetDateTime.now());
        inventoryBalanceRepository.save(inv);

        // Init Redis
        String key = "flashsale:" + sale.getId();
        redisTemplate.opsForValue().set(key + ":stock", String.valueOf(totalStock));
        Map<String, String> meta = new HashMap<>();
        meta.put("status", "ACTIVE");
        meta.put("startTime", String.valueOf(sale.getStartTime().toEpochSecond()));
        meta.put("endTime", String.valueOf(sale.getEndTime().toEpochSecond()));
        redisTemplate.opsForHash().putAll(key + ":metadata", meta);

        AtomicInteger success = new AtomicInteger(0);
        AtomicInteger reject = new AtomicInteger(0);
        
        ExecutorService executor = Executors.newFixedThreadPool(100);
        for(int i = 0; i < requests; i++) {
            final Long uid = u.getId();
            final Long sid = sale.getId();
            executor.submit(() -> {
                try {
                    reservationService.reserve(uid, sid, 1, UUID.randomUUID().toString());
                    success.incrementAndGet();
                } catch (Exception e) {
                    reject.incrementAndGet();
                }
            });
        }
        
        executor.shutdown();
        executor.awaitTermination(2, TimeUnit.MINUTES);

        assertEquals(totalStock, success.get(), "Successful reservations should exactly equal total stock");
        
        InventoryBalance finalInv = inventoryBalanceRepository.findById(sale.getId()).get();
        assertEquals(totalStock, finalInv.getReservedStock() + finalInv.getSoldStock(), "Durable stock should match");
        assertEquals(totalStock, finalInv.getTotalStock(), "Total stock unchanged");
    }
}
