import os

base_pkg = "/Users/amitesh/Documents/antigravity/optimistic-pascal/flash-sale-system/backend/src/main/java/com/flashsale"

def write_file(path, content):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        f.write(content)

java_files = {
    "auth/AuthService.java": """package com.flashsale.auth;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.Optional;

@Service
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${jwt.secret}")
    private String jwtSecret;
    @Value("${jwt.expiration-ms}")
    private long jwtExpirationMs;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public String register(String name, String email, String password) {
        if (userRepository.findByEmail(email).isPresent()) {
            throw new RuntimeException("Email already in use");
        }
        User user = new User();
        user.setName(name);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRole("USER");
        user.setCreatedAt(OffsetDateTime.now());
        userRepository.save(user);
        return generateToken(user);
    }

    public String login(String email, String password) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Invalid credentials"));
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new RuntimeException("Invalid credentials");
        }
        return generateToken(user);
    }

    private String generateToken(User user) {
        return Jwts.builder()
                .subject(user.getEmail())
                .claim("userId", user.getId())
                .claim("role", user.getRole())
                .issuedAt(new Date())
                .expiration(new Date((new Date()).getTime() + jwtExpirationMs))
                .signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes()))
                .compact();
    }
}
""",
    "reservation/ReservationService.java": """package com.flashsale.reservation;

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
    private final RedisTemplate<String, Object> redisTemplate;
    private final DefaultRedisScript<Long> reserveStockScript;

    @Value("${flashsale.reservation.timeout-seconds:60}")
    private int timeoutSeconds;

    public ReservationService(ReservationRepository reservationRepository,
                              InventoryBalanceRepository inventoryBalanceRepository,
                              FlashSaleRepository flashSaleRepository,
                              IdempotencyRecordRepository idempotencyRepository,
                              RedisTemplate<String, Object> redisTemplate,
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
        Object result = redisTemplate.execute(reserveStockScript, Collections.singletonList(saleKey), String.valueOf(quantity), String.valueOf(now));
        
        if (result instanceof java.util.Map) {
            java.util.Map errMap = (java.util.Map) result;
            throw new RuntimeException("Admission failed: " + errMap.get("err"));
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
""",
    "payment/PaymentService.java": """package com.flashsale.payment;

import com.flashsale.reservation.Reservation;
import com.flashsale.reservation.ReservationRepository;
import com.flashsale.inventory.InventoryBalanceRepository;
import com.flashsale.messaging.OutboxEvent;
import com.flashsale.messaging.OutboxEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;

@Service
public class PaymentService {
    private final PaymentRepository paymentRepository;
    private final ReservationRepository reservationRepository;
    private final InventoryBalanceRepository inventoryBalanceRepository;
    private final OutboxEventRepository outboxEventRepository;

    @Value("${flashsale.payment.success-probability:0.9}")
    private double successProbability;

    public PaymentService(PaymentRepository paymentRepository,
                          ReservationRepository reservationRepository,
                          InventoryBalanceRepository inventoryBalanceRepository,
                          OutboxEventRepository outboxEventRepository) {
        this.paymentRepository = paymentRepository;
        this.reservationRepository = reservationRepository;
        this.inventoryBalanceRepository = inventoryBalanceRepository;
        this.outboxEventRepository = outboxEventRepository;
    }

    @Transactional
    public Payment processPayment(Long userId, String reservationId, String idempotencyKey) {
        var existingOpt = paymentRepository.findByReservationIdAndIdempotencyKey(reservationId, idempotencyKey);
        if (existingOpt.isPresent()) {
            return existingOpt.get();
        }

        Reservation res = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new RuntimeException("Reservation not found"));

        if (!res.getUserId().equals(userId)) {
            throw new RuntimeException("Unauthorized");
        }

        if (!res.getStatus().equals("RESERVED")) {
            throw new RuntimeException("Invalid reservation state");
        }

        if (res.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new RuntimeException("Reservation expired");
        }

        boolean success = Math.random() < successProbability;
        
        Payment p = new Payment();
        p.setId(UUID.randomUUID().toString());
        p.setReservationId(reservationId);
        p.setIdempotencyKey(idempotencyKey);
        p.setAttemptNumber(1);
        p.setAmount(res.getUnitPrice().multiply(java.math.BigDecimal.valueOf(res.getQuantity())));
        p.setStatus(success ? "SUCCESS" : "FAILED");
        p.setMockTransactionReference("MOCK-" + UUID.randomUUID().toString().substring(0, 8));
        p.setCreatedAt(OffsetDateTime.now());
        p.setUpdatedAt(OffsetDateTime.now());
        paymentRepository.save(p);

        if (success) {
            reservationRepository.updateStatus(reservationId, "RESERVED", "PAYMENT_SUCCEEDED_PENDING_ORDER");
            inventoryBalanceRepository.commitStock(res.getSaleId(), res.getQuantity());

            OutboxEvent event = new OutboxEvent();
            event.setId(UUID.randomUUID().toString());
            event.setEventType("ORDER_CONFIRMATION");
            event.setAggregateId(reservationId);
            event.setPayload("{\\"reservationId\\":\\"" + reservationId + "\\"}");
            event.setCreatedAt(OffsetDateTime.now());
            outboxEventRepository.save(event);
        }

        return p;
    }
}
"""
}

for path, content in java_files.items():
    write_file(f"{base_pkg}/{path}", content)
