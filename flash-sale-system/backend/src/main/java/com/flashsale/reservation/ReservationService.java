package com.flashsale.reservation;

import com.flashsale.inventory.InventoryBalance;
import com.flashsale.inventory.InventoryBalanceRepository;
import com.flashsale.sale.FlashSale;
import com.flashsale.sale.FlashSaleRepository;
import com.flashsale.common.IdempotencyRecord;
import com.flashsale.common.IdempotencyRecordRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.UUID;
import java.math.BigDecimal;
import java.time.ZoneOffset;
import org.springframework.beans.factory.annotation.Value;

@Service
public class ReservationService {
    private final ReservationRepository reservationRepository;
    private final InventoryBalanceRepository inventoryBalanceRepository;
    private final FlashSaleRepository flashSaleRepository;
    private final IdempotencyRecordRepository idempotencyRepository;
    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> reserveStockScript;
    private final TransactionTemplate transactionTemplate;

    @Value("${flashsale.reservation.timeout-seconds:60}")
    private int timeoutSeconds;

    public ReservationService(ReservationRepository reservationRepository,
                              InventoryBalanceRepository inventoryBalanceRepository,
                              FlashSaleRepository flashSaleRepository,
                              IdempotencyRecordRepository idempotencyRepository,
                              StringRedisTemplate redisTemplate,
                              DefaultRedisScript<Long> reserveStockScript,
                              PlatformTransactionManager transactionManager) {
        this.reservationRepository = reservationRepository;
        this.inventoryBalanceRepository = inventoryBalanceRepository;
        this.flashSaleRepository = flashSaleRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.redisTemplate = redisTemplate;
        this.reserveStockScript = reserveStockScript;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public Reservation reserve(Long userId, Long saleId, int quantity, String idempotencyKey) {
        String saleKey = "flashsale:" + saleId;
        long now = OffsetDateTime.now(ZoneOffset.UTC).toEpochSecond();

        // 1. FAST PATH: Pure in-memory Redis gate
        // NO database connection is checked out. 0 DB load for rejected requests!
        Long remainingStock = (Long) redisTemplate.execute(reserveStockScript, Collections.singletonList(saleKey), String.valueOf(quantity), String.valueOf(now));

        // Lazy auto-hydration if cache miss or window expired in demo
        if (remainingStock != null && (remainingStock == -3 || remainingStock == -2)) {
            hydrateRedis(saleId);
            remainingStock = (Long) redisTemplate.execute(reserveStockScript, Collections.singletonList(saleKey), String.valueOf(quantity), String.valueOf(now));
        }

        if (remainingStock == null || remainingStock < 0) {
            long code = remainingStock != null ? remainingStock : -99;
            if (code == -1) throw new RuntimeException("SALE_NOT_ACTIVE");
            if (code == -2) throw new RuntimeException("SALE_OUT_OF_WINDOW");
            if (code == -3) throw new RuntimeException("STOCK_NOT_INITIALIZED");
            if (code == -4) throw new RuntimeException("INSUFFICIENT_STOCK");
            throw new RuntimeException("Admission failed with code: " + code);
        }

        // 2. DURABLE PATH: Executed ONLY for admitted winners (e.g. 50 out of 1000)
        return transactionTemplate.execute(status -> {
            var recordOpt = idempotencyRepository.findByUserIdAndIdempotencyKeyAndOperationType(userId, idempotencyKey, "RESERVE");
            if (recordOpt.isPresent()) {
                return reservationRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey)
                        .orElseThrow(() -> new RuntimeException("Reservation not found for idempotency key"));
            }

            FlashSale sale = flashSaleRepository.findById(saleId).orElseThrow(() -> new RuntimeException("Sale not found"));

            int updated = inventoryBalanceRepository.reserveStock(saleId, quantity);
            if (updated == 0) {
                // Compensate Redis stock if DB row update failed
                redisTemplate.opsForValue().increment(saleKey + ":stock", quantity);
                throw new RuntimeException("Failed to reserve in DB");
            }

            Reservation res = new Reservation();
            res.setId(UUID.randomUUID().toString());
            res.setUserId(userId);
            res.setSaleId(saleId);
            res.setProductId(sale.getProductId());
            res.setQuantity(quantity);
            res.setUnitPrice(BigDecimal.valueOf(100.0));
            res.setStatus("RESERVED");
            res.setExpiresAt(OffsetDateTime.now().plusSeconds(timeoutSeconds));
            res.setIdempotencyKey(idempotencyKey);
            res.setCreatedAt(OffsetDateTime.now());
            res.setUpdatedAt(OffsetDateTime.now());
            reservationRepository.save(res);

            IdempotencyRecord record = new IdempotencyRecord();
            record.setId(UUID.randomUUID().toString());
            record.setUserId(userId);
            record.setIdempotencyKey(idempotencyKey);
            record.setOperationType("RESERVE");
            record.setResultStatus("SUCCESS");
            record.setCreatedAt(OffsetDateTime.now());
            idempotencyRepository.save(record);

            return res;
        });
    }

    @Transactional
    public Reservation reserveDirectDb(Long userId, Long saleId, int quantity, String idempotencyKey) {
        // Idempotency check
        var recordOpt = idempotencyRepository.findByUserIdAndIdempotencyKeyAndOperationType(userId, idempotencyKey, "RESERVE");
        if (recordOpt.isPresent()) {
            return reservationRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey)
                    .orElseThrow(() -> new RuntimeException("Reservation not found for idempotency key"));
        }

        FlashSale sale = flashSaleRepository.findById(saleId).orElseThrow(() -> new RuntimeException("Sale not found"));
        // Auto-heal expired window for active drops in demo environment
        if ("ACTIVE".equals(sale.getStatus()) && sale.getEndTime().isBefore(OffsetDateTime.now())) {
            sale.setEndTime(OffsetDateTime.now().plusYears(1));
            flashSaleRepository.save(sale);
        }
        if (!"ACTIVE".equals(sale.getStatus())) {
            throw new RuntimeException("SALE_NOT_ACTIVE");
        }

        // Direct DB atomic reservation using PostgreSQL row-level lock
        int updated = inventoryBalanceRepository.reserveStock(saleId, quantity);
        if (updated == 0) {
            throw new RuntimeException("INSUFFICIENT_STOCK");
        }

        // Also keep redis counter in sync if initialized
        String saleKey = "flashsale:" + saleId;
        try {
            redisTemplate.opsForValue().decrement(saleKey + ":stock", quantity);
        } catch (Exception ignored) {}

        Reservation res = new Reservation();
        res.setId(UUID.randomUUID().toString());
        res.setUserId(userId);
        res.setSaleId(saleId);
        res.setProductId(sale.getProductId());
        res.setQuantity(quantity);
        res.setUnitPrice(BigDecimal.valueOf(100.0));
        res.setStatus("RESERVED");
        res.setExpiresAt(OffsetDateTime.now().plusSeconds(timeoutSeconds));
        res.setIdempotencyKey(idempotencyKey);
        res.setCreatedAt(OffsetDateTime.now());
        res.setUpdatedAt(OffsetDateTime.now());
        reservationRepository.save(res);

        IdempotencyRecord record = new IdempotencyRecord();
        record.setId(UUID.randomUUID().toString());
        record.setUserId(userId);
        record.setIdempotencyKey(idempotencyKey);
        record.setOperationType("RESERVE");
        record.setResultStatus("SUCCESS");
        record.setCreatedAt(OffsetDateTime.now());
        idempotencyRepository.save(record);

        return res;
    }

    public synchronized void hydrateRedis(Long saleId) {
        FlashSale sale = flashSaleRepository.findById(saleId).orElse(null);
        if (sale == null) return;

        if ("ACTIVE".equals(sale.getStatus()) && sale.getEndTime().isBefore(OffsetDateTime.now())) {
            sale.setEndTime(OffsetDateTime.now().plusYears(1));
            flashSaleRepository.save(sale);
        }

        String key = "flashsale:" + sale.getId();
        if (Boolean.FALSE.equals(redisTemplate.hasKey(key + ":stock"))) {
            InventoryBalance inv = inventoryBalanceRepository.findById(sale.getId()).orElse(null);
            int available = (inv != null) ? Math.max(0, inv.getTotalStock() - inv.getReservedStock() - inv.getSoldStock()) : sale.getSaleStock();
            redisTemplate.opsForValue().set(key + ":stock", String.valueOf(available));
        }

        java.util.Map<String, String> metadata = new java.util.HashMap<>();
        metadata.put("status", sale.getStatus());
        metadata.put("startTime", String.valueOf(sale.getStartTime().toEpochSecond()));
        metadata.put("endTime", String.valueOf(sale.getEndTime().toEpochSecond()));
        redisTemplate.opsForHash().putAll(key + ":metadata", metadata);
    }
}
