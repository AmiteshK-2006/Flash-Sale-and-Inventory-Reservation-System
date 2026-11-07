package com.flashsale.reservation;

import com.flashsale.inventory.InventoryBalanceRepository;
import com.flashsale.sale.FlashSale;
import com.flashsale.sale.FlashSaleRepository;
import com.flashsale.common.IdempotencyRecord;
import com.flashsale.common.IdempotencyRecordRepository;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
    private final org.springframework.data.redis.core.StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> reserveStockScript;

    @Value("${flashsale.reservation.timeout-seconds:60}")
    private int timeoutSeconds;

    public ReservationService(ReservationRepository reservationRepository,
                              InventoryBalanceRepository inventoryBalanceRepository,
                              FlashSaleRepository flashSaleRepository,
                              IdempotencyRecordRepository idempotencyRepository,
                              org.springframework.data.redis.core.StringRedisTemplate redisTemplate,
                              DefaultRedisScript<Long> reserveStockScript) {
        this.reservationRepository = reservationRepository;
        this.inventoryBalanceRepository = inventoryBalanceRepository;
        this.flashSaleRepository = flashSaleRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.redisTemplate = redisTemplate;
        this.reserveStockScript = reserveStockScript;
    }

    @Transactional
    public Reservation reserve(Long userId, Long saleId, int quantity, String idempotencyKey) {
        // Idempotency check
        var recordOpt = idempotencyRepository.findByUserIdAndIdempotencyKeyAndOperationType(userId, idempotencyKey, "RESERVE");
        if (recordOpt.isPresent()) {
            return reservationRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey)
                    .orElseThrow(() -> new RuntimeException("Reservation not found for idempotency key"));
        }

        FlashSale sale = flashSaleRepository.findById(saleId).orElseThrow(() -> new RuntimeException("Sale not found"));
        
        // Redis admission
        String saleKey = "flashsale:" + saleId;
        long now = OffsetDateTime.now(ZoneOffset.UTC).toEpochSecond();
        Long remainingStock = (Long) redisTemplate.execute(reserveStockScript, Collections.singletonList(saleKey), String.valueOf(quantity), String.valueOf(now));
        if (remainingStock == null || remainingStock < 0) {
            long code = remainingStock != null ? remainingStock : -99;
            if (code == -1) throw new RuntimeException("SALE_NOT_ACTIVE");
            if (code == -2) throw new RuntimeException("SALE_OUT_OF_WINDOW");
            if (code == -3) throw new RuntimeException("STOCK_NOT_INITIALIZED");
            if (code == -4) throw new RuntimeException("INSUFFICIENT_STOCK");
            throw new RuntimeException("Admission failed with code: " + code);
        }

        // DB durable reserve
        int updated = inventoryBalanceRepository.reserveStock(saleId, quantity);
        if (updated == 0) {
            // compensate redis stock
            redisTemplate.opsForValue().increment(saleKey + ":stock", quantity);
            throw new RuntimeException("Failed to reserve in DB");
        }

        Reservation res = new Reservation();
        res.setId(UUID.randomUUID().toString());
        res.setUserId(userId);
        res.setSaleId(saleId);
        res.setProductId(sale.getProductId());
        res.setQuantity(quantity);
        res.setUnitPrice(BigDecimal.valueOf(100.0)); // Should come from product
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
}
