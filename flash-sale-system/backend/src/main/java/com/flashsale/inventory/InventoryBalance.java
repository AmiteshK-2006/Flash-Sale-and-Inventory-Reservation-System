package com.flashsale.inventory;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "inventory_balances")
public class InventoryBalance {
    @Id
    private Long saleId;
    private Integer totalStock;
    private Integer reservedStock;
    private Integer soldStock;
    private OffsetDateTime updatedAt;
    // Getters and Setters
    public Long getSaleId() { return saleId; } public void setSaleId(Long saleId) { this.saleId = saleId; }
    public Integer getTotalStock() { return totalStock; } public void setTotalStock(Integer totalStock) { this.totalStock = totalStock; }
    public Integer getReservedStock() { return reservedStock; } public void setReservedStock(Integer reservedStock) { this.reservedStock = reservedStock; }
    public Integer getSoldStock() { return soldStock; } public void setSoldStock(Integer soldStock) { this.soldStock = soldStock; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; } public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
