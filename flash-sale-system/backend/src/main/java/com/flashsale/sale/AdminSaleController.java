package com.flashsale.sale;

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
    private final com.flashsale.product.ProductRepository productRepository;
    private final StringRedisTemplate redisTemplate;

    public AdminSaleController(FlashSaleRepository flashSaleRepository, 
                               InventoryBalanceRepository inventoryBalanceRepository,
                               com.flashsale.product.ProductRepository productRepository,
                               StringRedisTemplate redisTemplate) {
        this.flashSaleRepository = flashSaleRepository;
        this.inventoryBalanceRepository = inventoryBalanceRepository;
        this.productRepository = productRepository;
        this.redisTemplate = redisTemplate;
    }

    @PostMapping
    public ResponseEntity<?> createSale(@RequestBody FlashSale sale) {
        if (sale.getProductId() == null || !productRepository.existsById(sale.getProductId())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Product not found with id: " + sale.getProductId()));
        }
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
