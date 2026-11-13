package com.flashsale.sale;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import java.util.List;

import com.flashsale.inventory.InventoryBalance;
import com.flashsale.inventory.InventoryBalanceRepository;
import com.flashsale.product.Product;
import com.flashsale.product.ProductRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.util.ArrayList;
import java.util.Map;
import java.util.LinkedHashMap;

@RestController
@RequestMapping("/api/sales")
public class SaleController {
    private final FlashSaleRepository flashSaleRepository;
    private final InventoryBalanceRepository inventoryBalanceRepository;
    private final ProductRepository productRepository;
    private final StringRedisTemplate redisTemplate;

    public SaleController(FlashSaleRepository flashSaleRepository,
                          InventoryBalanceRepository inventoryBalanceRepository,
                          ProductRepository productRepository,
                          StringRedisTemplate redisTemplate) {
        this.flashSaleRepository = flashSaleRepository;
        this.inventoryBalanceRepository = inventoryBalanceRepository;
        this.productRepository = productRepository;
        this.redisTemplate = redisTemplate;
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getSales() {
        List<FlashSale> sales = flashSaleRepository.findAll();
        List<Map<String, Object>> result = new ArrayList<>();

        for (FlashSale sale : sales) {
            // Auto-heal expired window for active drops in demo environment
            if ("ACTIVE".equals(sale.getStatus()) && sale.getEndTime().isBefore(java.time.OffsetDateTime.now())) {
                sale.setEndTime(java.time.OffsetDateTime.now().plusYears(1));
                flashSaleRepository.save(sale);
            }

            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", sale.getId());
            map.put("productId", sale.getProductId());
            map.put("saleStock", sale.getSaleStock());
            map.put("maxQuantityPerPurchase", sale.getMaxQuantityPerPurchase());
            map.put("status", sale.getStatus());
            map.put("startTime", sale.getStartTime());
            map.put("endTime", sale.getEndTime());

            // Product info
            productRepository.findById(sale.getProductId()).ifPresent(prod -> {
                map.put("productName", prod.getName());
                map.put("price", prod.getPrice());
            });

            // Live remaining stock from Redis
            int remaining = 0;
            String redisStock = redisTemplate.opsForValue().get("flashsale:" + sale.getId() + ":stock");
            if (redisStock != null) {
                try {
                    remaining = Integer.parseInt(redisStock);
                } catch (Exception ignored) {}
            } else {
                InventoryBalance inv = inventoryBalanceRepository.findById(sale.getId()).orElse(null);
                if (inv != null) {
                    remaining = inv.getTotalStock() - inv.getReservedStock() - inv.getSoldStock();
                } else {
                    remaining = sale.getSaleStock();
                }
                try {
                    String sKey = "flashsale:" + sale.getId();
                    redisTemplate.opsForValue().set(sKey + ":stock", String.valueOf(Math.max(0, remaining)));
                } catch (Exception ignored) {}
            }
            map.put("availableStock", Math.max(0, remaining));

            // Always ensure metadata hash in Redis is synchronized with Postgres
            try {
                String sKey = "flashsale:" + sale.getId();
                Map<String, String> meta = new java.util.HashMap<>();
                meta.put("status", sale.getStatus());
                meta.put("startTime", String.valueOf(sale.getStartTime().toEpochSecond()));
                meta.put("endTime", String.valueOf(sale.getEndTime().toEpochSecond()));
                redisTemplate.opsForHash().putAll(sKey + ":metadata", meta);
            } catch (Exception ignored) {}

            InventoryBalance inv = inventoryBalanceRepository.findById(sale.getId()).orElse(null);
            if (inv != null) {
                map.put("soldStock", inv.getSoldStock());
                map.put("reservedStock", inv.getReservedStock());
            }

            result.add(map);
        }

        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getSale(@PathVariable Long id) {
        return flashSaleRepository.findById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/availability")
    public ResponseEntity<?> getAvailability(@PathVariable Long id) {
        String redisStock = redisTemplate.opsForValue().get("flashsale:" + id + ":stock");
        int remaining = 0;
        if (redisStock != null) {
            try {
                remaining = Integer.parseInt(redisStock);
            } catch (Exception ignored) {}
        } else {
            InventoryBalance inv = inventoryBalanceRepository.findById(id).orElse(null);
            if (inv != null) {
                remaining = inv.getTotalStock() - inv.getReservedStock() - inv.getSoldStock();
            }
        }
        return ResponseEntity.ok(Map.of("saleId", id, "availableStock", Math.max(0, remaining)));
    }
}
