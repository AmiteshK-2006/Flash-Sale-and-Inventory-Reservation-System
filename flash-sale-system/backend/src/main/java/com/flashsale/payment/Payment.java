package com.flashsale.payment;

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
