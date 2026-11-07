package com.flashsale.sale;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "flash_sales")
public class FlashSale {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long productId;
    private OffsetDateTime startTime;
    private OffsetDateTime endTime;
    private Integer saleStock;
    private Integer maxQuantityPerPurchase;
    private String status;
    private OffsetDateTime createdAt;
    // Getters and Setters
    public Long getId() { return id; } public void setId(Long id) { this.id = id; }
    public Long getProductId() { return productId; } public void setProductId(Long productId) { this.productId = productId; }
    public OffsetDateTime getStartTime() { return startTime; } public void setStartTime(OffsetDateTime startTime) { this.startTime = startTime; }
    public OffsetDateTime getEndTime() { return endTime; } public void setEndTime(OffsetDateTime endTime) { this.endTime = endTime; }
    public Integer getSaleStock() { return saleStock; } public void setSaleStock(Integer saleStock) { this.saleStock = saleStock; }
    public Integer getMaxQuantityPerPurchase() { return maxQuantityPerPurchase; } public void setMaxQuantityPerPurchase(Integer maxQuantityPerPurchase) { this.maxQuantityPerPurchase = maxQuantityPerPurchase; }
    public String getStatus() { return status; } public void setStatus(String status) { this.status = status; }
    public OffsetDateTime getCreatedAt() { return createdAt; } public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
