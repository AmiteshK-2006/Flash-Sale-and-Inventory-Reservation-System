import os

base_pkg = "/Users/amitesh/Documents/antigravity/optimistic-pascal/flash-sale-system/backend/src/main/java/com/flashsale"

def write_file(path, content):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        f.write(content)

entities = {
    "auth/User.java": """package com.flashsale.auth;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "users")
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private String email;
    private String passwordHash;
    private String role;
    private OffsetDateTime createdAt;
    // Getters and Setters
    public Long getId() { return id; } public void setId(Long id) { this.id = id; }
    public String getName() { return name; } public void setName(String name) { this.name = name; }
    public String getEmail() { return email; } public void setEmail(String email) { this.email = email; }
    public String getPasswordHash() { return passwordHash; } public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getRole() { return role; } public void setRole(String role) { this.role = role; }
    public OffsetDateTime getCreatedAt() { return createdAt; } public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
""",
    "product/Product.java": """package com.flashsale.product;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "products")
public class Product {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private String description;
    private String imageUrl;
    private BigDecimal price;
    private String category;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    // Getters and Setters
    public Long getId() { return id; } public void setId(Long id) { this.id = id; }
    public String getName() { return name; } public void setName(String name) { this.name = name; }
    public String getDescription() { return description; } public void setDescription(String description) { this.description = description; }
    public String getImageUrl() { return imageUrl; } public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public BigDecimal getPrice() { return price; } public void setPrice(BigDecimal price) { this.price = price; }
    public String getCategory() { return category; } public void setCategory(String category) { this.category = category; }
    public OffsetDateTime getCreatedAt() { return createdAt; } public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; } public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
""",
    "sale/FlashSale.java": """package com.flashsale.sale;

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
""",
    "inventory/InventoryBalance.java": """package com.flashsale.inventory;

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
""",
    "reservation/Reservation.java": """package com.flashsale.reservation;

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
""",
    "payment/Payment.java": """package com.flashsale.payment;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "payments")
public class Payment {
    @Id
    private String id;
    private String reservationId;
    private String idempotencyKey;
    private Integer attemptNumber;
    private BigDecimal amount;
    private String status;
    private String mockTransactionReference;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    // Getters and Setters
    public String getId() { return id; } public void setId(String id) { this.id = id; }
    public String getReservationId() { return reservationId; } public void setReservationId(String reservationId) { this.reservationId = reservationId; }
    public String getIdempotencyKey() { return idempotencyKey; } public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public Integer getAttemptNumber() { return attemptNumber; } public void setAttemptNumber(Integer attemptNumber) { this.attemptNumber = attemptNumber; }
    public BigDecimal getAmount() { return amount; } public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getStatus() { return status; } public void setStatus(String status) { this.status = status; }
    public String getMockTransactionReference() { return mockTransactionReference; } public void setMockTransactionReference(String mockTransactionReference) { this.mockTransactionReference = mockTransactionReference; }
    public OffsetDateTime getCreatedAt() { return createdAt; } public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; } public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
""",
    "order/Order.java": """package com.flashsale.order;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "orders")
public class Order {
    @Id
    private String id;
    private String reservationId;
    private Long userId;
    private Long productId;
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal totalAmount;
    private String status;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    // Getters and Setters
    public String getId() { return id; } public void setId(String id) { this.id = id; }
    public String getReservationId() { return reservationId; } public void setReservationId(String reservationId) { this.reservationId = reservationId; }
    public Long getUserId() { return userId; } public void setUserId(Long userId) { this.userId = userId; }
    public Long getProductId() { return productId; } public void setProductId(Long productId) { this.productId = productId; }
    public Integer getQuantity() { return quantity; } public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; } public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
    public BigDecimal getTotalAmount() { return totalAmount; } public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    public String getStatus() { return status; } public void setStatus(String status) { this.status = status; }
    public OffsetDateTime getCreatedAt() { return createdAt; } public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; } public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
""",
    "messaging/OutboxEvent.java": """package com.flashsale.messaging;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "outbox_events")
public class OutboxEvent {
    @Id
    private String id;
    private String eventType;
    private String aggregateId;
    private String payload;
    private OffsetDateTime createdAt;
    private OffsetDateTime publishedAt;
    private Integer retryCount;
    private String lastError;
    // Getters and Setters
    public String getId() { return id; } public void setId(String id) { this.id = id; }
    public String getEventType() { return eventType; } public void setEventType(String eventType) { this.eventType = eventType; }
    public String getAggregateId() { return aggregateId; } public void setAggregateId(String aggregateId) { this.aggregateId = aggregateId; }
    public String getPayload() { return payload; } public void setPayload(String payload) { this.payload = payload; }
    public OffsetDateTime getCreatedAt() { return createdAt; } public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getPublishedAt() { return publishedAt; } public void setPublishedAt(OffsetDateTime publishedAt) { this.publishedAt = publishedAt; }
    public Integer getRetryCount() { return retryCount; } public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }
    public String getLastError() { return lastError; } public void setLastError(String lastError) { this.lastError = lastError; }
}
""",
    "common/IdempotencyRecord.java": """package com.flashsale.common;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "idempotency_records")
public class IdempotencyRecord {
    @Id
    private String id;
    private Long userId;
    private String idempotencyKey;
    private String operationType;
    private String fingerprint;
    private String resultStatus;
    private String resultPayload;
    private OffsetDateTime createdAt;
    private OffsetDateTime expiresAt;
    // Getters and Setters
    public String getId() { return id; } public void setId(String id) { this.id = id; }
    public Long getUserId() { return userId; } public void setUserId(Long userId) { this.userId = userId; }
    public String getIdempotencyKey() { return idempotencyKey; } public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public String getOperationType() { return operationType; } public void setOperationType(String operationType) { this.operationType = operationType; }
    public String getFingerprint() { return fingerprint; } public void setFingerprint(String fingerprint) { this.fingerprint = fingerprint; }
    public String getResultStatus() { return resultStatus; } public void setResultStatus(String resultStatus) { this.resultStatus = resultStatus; }
    public String getResultPayload() { return resultPayload; } public void setResultPayload(String resultPayload) { this.resultPayload = resultPayload; }
    public OffsetDateTime getCreatedAt() { return createdAt; } public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getExpiresAt() { return expiresAt; } public void setExpiresAt(OffsetDateTime expiresAt) { this.expiresAt = expiresAt; }
}
""",
    "simulation/SimulationRun.java": """package com.flashsale.simulation;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "simulation_runs")
public class SimulationRun {
    @Id
    private String id;
    private String configuration; // JSON
    private String status;
    private Integer totalRequests;
    private Integer successfulReservations;
    private Integer rejectedRequests;
    private Integer failedRequests;
    private Long durationMs;
    private Double throughput;
    private String latencyStatistics; // JSON
    private OffsetDateTime createdAt;
    private OffsetDateTime completedAt;
    // Getters and Setters
    public String getId() { return id; } public void setId(String id) { this.id = id; }
    public String getConfiguration() { return configuration; } public void setConfiguration(String configuration) { this.configuration = configuration; }
    public String getStatus() { return status; } public void setStatus(String status) { this.status = status; }
    public Integer getTotalRequests() { return totalRequests; } public void setTotalRequests(Integer totalRequests) { this.totalRequests = totalRequests; }
    public Integer getSuccessfulReservations() { return successfulReservations; } public void setSuccessfulReservations(Integer successfulReservations) { this.successfulReservations = successfulReservations; }
    public Integer getRejectedRequests() { return rejectedRequests; } public void setRejectedRequests(Integer rejectedRequests) { this.rejectedRequests = rejectedRequests; }
    public Integer getFailedRequests() { return failedRequests; } public void setFailedRequests(Integer failedRequests) { this.failedRequests = failedRequests; }
    public Long getDurationMs() { return durationMs; } public void setDurationMs(Long durationMs) { this.durationMs = durationMs; }
    public Double getThroughput() { return throughput; } public void setThroughput(Double throughput) { this.throughput = throughput; }
    public String getLatencyStatistics() { return latencyStatistics; } public void setLatencyStatistics(String latencyStatistics) { this.latencyStatistics = latencyStatistics; }
    public OffsetDateTime getCreatedAt() { return createdAt; } public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getCompletedAt() { return completedAt; } public void setCompletedAt(OffsetDateTime completedAt) { this.completedAt = completedAt; }
}
"""
}

repos = {
    "auth/UserRepository.java": """package com.flashsale.auth;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
}""",
    "product/ProductRepository.java": """package com.flashsale.product;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ProductRepository extends JpaRepository<Product, Long> {}""",
    "sale/FlashSaleRepository.java": """package com.flashsale.sale;
import org.springframework.data.jpa.repository.JpaRepository;
public interface FlashSaleRepository extends JpaRepository<FlashSale, Long> {}""",
    "inventory/InventoryBalanceRepository.java": """package com.flashsale.inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryBalanceRepository extends JpaRepository<InventoryBalance, Long> {
    @Modifying
    @Query("UPDATE InventoryBalance i SET i.reservedStock = i.reservedStock + :quantity WHERE i.saleId = :saleId AND i.reservedStock + i.soldStock + :quantity <= i.totalStock")
    int reserveStock(@Param("saleId") Long saleId, @Param("quantity") int quantity);

    @Modifying
    @Query("UPDATE InventoryBalance i SET i.reservedStock = i.reservedStock - :quantity, i.soldStock = i.soldStock + :quantity WHERE i.saleId = :saleId AND i.reservedStock >= :quantity")
    int commitStock(@Param("saleId") Long saleId, @Param("quantity") int quantity);

    @Modifying
    @Query("UPDATE InventoryBalance i SET i.reservedStock = i.reservedStock - :quantity WHERE i.saleId = :saleId AND i.reservedStock >= :quantity")
    int releaseStock(@Param("saleId") Long saleId, @Param("quantity") int quantity);
}""",
    "reservation/ReservationRepository.java": """package com.flashsale.reservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface ReservationRepository extends JpaRepository<Reservation, String> {
    Optional<Reservation> findByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);
    List<Reservation> findByUserId(Long userId);
    List<Reservation> findByStatusAndExpiresAtBefore(String status, OffsetDateTime time);
    
    @Modifying
    @Query("UPDATE Reservation r SET r.status = :newStatus WHERE r.id = :id AND r.status = :oldStatus")
    int updateStatus(@Param("id") String id, @Param("oldStatus") String oldStatus, @Param("newStatus") String newStatus);
}""",
    "payment/PaymentRepository.java": """package com.flashsale.payment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface PaymentRepository extends JpaRepository<Payment, String> {
    Optional<Payment> findByReservationIdAndIdempotencyKey(String reservationId, String idempotencyKey);
}""",
    "order/OrderRepository.java": """package com.flashsale.order;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
public interface OrderRepository extends JpaRepository<Order, String> {
    Optional<Order> findByReservationId(String reservationId);
    List<Order> findByUserId(Long userId);
}""",
    "messaging/OutboxEventRepository.java": """package com.flashsale.messaging;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, String> {
    List<OutboxEvent> findByPublishedAtIsNullOrderByCreatedAtAsc();
}""",
    "common/IdempotencyRecordRepository.java": """package com.flashsale.common;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, String> {
    Optional<IdempotencyRecord> findByUserIdAndIdempotencyKeyAndOperationType(Long userId, String idempotencyKey, String operationType);
}""",
    "simulation/SimulationRunRepository.java": """package com.flashsale.simulation;
import org.springframework.data.jpa.repository.JpaRepository;
public interface SimulationRunRepository extends JpaRepository<SimulationRun, String> {}"""
}

for path, content in entities.items():
    write_file(f"{base_pkg}/{path}", content)

for path, content in repos.items():
    write_file(f"{base_pkg}/{path}", content)

write_file(f"{base_pkg}/BackendApplication.java", """package com.flashsale;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BackendApplication {
    public static void main(String[] args) {
        SpringApplication.run(BackendApplication.class, args);
    }
}
""")
