package com.flashsale.common;

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
