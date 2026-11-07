package com.flashsale.reservation;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "reservations")
public class Reservation {
    @Id
    private String id;
    private Long userId;
    private Long saleId;
    private Long productId;
    private Integer quantity;
    private BigDecimal unitPrice;
    private String status;
    private OffsetDateTime expiresAt;
    private String idempotencyKey;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    // Getters and Setters
    public String getId() { return id; } public void setId(String id) { this.id = id; }
    public Long getUserId() { return userId; } public void setUserId(Long userId) { this.userId = userId; }
    public Long getSaleId() { return saleId; } public void setSaleId(Long saleId) { this.saleId = saleId; }
    public Long getProductId() { return productId; } public void setProductId(Long productId) { this.productId = productId; }
    public Integer getQuantity() { return quantity; } public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; } public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
    public String getStatus() { return status; } public void setStatus(String status) { this.status = status; }
    public OffsetDateTime getExpiresAt() { return expiresAt; } public void setExpiresAt(OffsetDateTime expiresAt) { this.expiresAt = expiresAt; }
    public String getIdempotencyKey() { return idempotencyKey; } public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public OffsetDateTime getCreatedAt() { return createdAt; } public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; } public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
