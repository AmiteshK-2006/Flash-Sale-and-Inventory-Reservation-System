import os

base_pkg = "/Users/amitesh/Documents/antigravity/optimistic-pascal/flash-sale-system/backend/src/main/java/com/flashsale"

def write_file(path, content):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        f.write(content)

java_files = {
    "auth/AuthController.java": """package com.flashsale.auth;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import java.util.Map;
import java.util.HashMap;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> request) {
        try {
            String token = authService.register(request.get("name"), request.get("email"), request.get("password"));
            Map<String, String> resp = new HashMap<>();
            resp.put("token", token);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> request) {
        try {
            String token = authService.login(request.get("email"), request.get("password"));
            Map<String, String> resp = new HashMap<>();
            resp.put("token", token);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            return ResponseEntity.status(401).body(Map.of("error", e.getMessage()));
        }
    }
}
""",
    "reservation/ReservationController.java": """package com.flashsale.reservation;

import com.flashsale.config.CustomUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import java.util.Map;

@RestController
@RequestMapping("/api/sales/{saleId}/reservations")
public class ReservationController {
    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @PostMapping
    public ResponseEntity<?> reserve(@PathVariable Long saleId,
                                     @RequestBody Map<String, Integer> request,
                                     @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                     @AuthenticationPrincipal CustomUserDetails userDetails) {
        if (idempotencyKey == null || idempotencyKey.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Idempotency-Key header is required"));
        }
        int quantity = request.getOrDefault("quantity", 1);
        try {
            Reservation res = reservationService.reserve(userDetails.getId(), saleId, quantity, idempotencyKey);
            return ResponseEntity.ok(res);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
""",
    "payment/PaymentController.java": """package com.flashsale.payment;

import com.flashsale.config.CustomUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import java.util.Map;

@RestController
@RequestMapping("/api/reservations/{reservationId}/payments")
public class PaymentController {
    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    public ResponseEntity<?> processPayment(@PathVariable String reservationId,
                                            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                            @AuthenticationPrincipal CustomUserDetails userDetails) {
        if (idempotencyKey == null || idempotencyKey.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Idempotency-Key header is required"));
        }
        try {
            Payment payment = paymentService.processPayment(userDetails.getId(), reservationId, idempotencyKey);
            return ResponseEntity.ok(payment);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
""",
    "product/ProductController.java": """package com.flashsale.product;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import java.util.List;
import java.time.OffsetDateTime;

@RestController
@RequestMapping("/api/products")
public class ProductController {
    private final ProductRepository productRepository;

    public ProductController(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @GetMapping
    public ResponseEntity<List<Product>> getProducts() {
        return ResponseEntity.ok(productRepository.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getProduct(@PathVariable Long id) {
        return productRepository.findById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }
}
""",
    "sale/SaleController.java": """package com.flashsale.sale;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import java.util.List;

@RestController
@RequestMapping("/api/sales")
public class SaleController {
    private final FlashSaleRepository flashSaleRepository;

    public SaleController(FlashSaleRepository flashSaleRepository) {
        this.flashSaleRepository = flashSaleRepository;
    }

    @GetMapping
    public ResponseEntity<List<FlashSale>> getSales() {
        return ResponseEntity.ok(flashSaleRepository.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getSale(@PathVariable Long id) {
        return flashSaleRepository.findById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }
}
""",
    "sale/AdminSaleController.java": """package com.flashsale.sale;

import com.flashsale.inventory.InventoryBalance;
import com.flashsale.inventory.InventoryBalanceRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.HashMap;

@RestController
@RequestMapping("/api/admin/sales")
public class AdminSaleController {
    private final FlashSaleRepository flashSaleRepository;
    private final InventoryBalanceRepository inventoryBalanceRepository;
    private final StringRedisTemplate redisTemplate;

    public AdminSaleController(FlashSaleRepository flashSaleRepository, 
                               InventoryBalanceRepository inventoryBalanceRepository,
                               StringRedisTemplate redisTemplate) {
        this.flashSaleRepository = flashSaleRepository;
        this.inventoryBalanceRepository = inventoryBalanceRepository;
        this.redisTemplate = redisTemplate;
    }

    @PostMapping
    public ResponseEntity<?> createSale(@RequestBody FlashSale sale) {
        sale.setStatus("SCHEDULED");
        sale.setCreatedAt(OffsetDateTime.now());
        FlashSale saved = flashSaleRepository.save(sale);
        
        InventoryBalance inv = new InventoryBalance();
        inv.setSaleId(saved.getId());
        inv.setTotalStock(saved.getSaleStock());
        inv.setReservedStock(0);
        inv.setSoldStock(0);
        inv.setUpdatedAt(OffsetDateTime.now());
        inventoryBalanceRepository.save(inv);
        
        return ResponseEntity.ok(saved);
    }

    @PostMapping("/{saleId}/activate")
    public ResponseEntity<?> activateSale(@PathVariable Long saleId) {
        FlashSale sale = flashSaleRepository.findById(saleId)
            .orElseThrow(() -> new RuntimeException("Sale not found"));
        if (!"SCHEDULED".equals(sale.getStatus())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Sale must be scheduled to activate"));
        }
        
        InventoryBalance inv = inventoryBalanceRepository.findById(saleId)
            .orElseThrow(() -> new RuntimeException("Inventory not found"));
        
        // Init redis
        String key = "flashsale:" + saleId;
        redisTemplate.opsForValue().setIfAbsent(key + ":stock", String.valueOf(inv.getTotalStock() - inv.getReservedStock() - inv.getSoldStock()));
        
        Map<String, String> metadata = new HashMap<>();
        metadata.put("status", "ACTIVE");
        metadata.put("startTime", String.valueOf(sale.getStartTime().toEpochSecond()));
        metadata.put("endTime", String.valueOf(sale.getEndTime().toEpochSecond()));
        redisTemplate.opsForHash().putAll(key + ":metadata", metadata);
        
        sale.setStatus("ACTIVE");
        flashSaleRepository.save(sale);
        
        return ResponseEntity.ok(sale);
    }
}
"""
}

for path, content in java_files.items():
    write_file(f"{base_pkg}/{path}", content)
