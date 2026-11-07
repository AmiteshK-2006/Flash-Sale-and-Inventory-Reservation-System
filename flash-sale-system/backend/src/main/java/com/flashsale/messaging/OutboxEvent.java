package com.flashsale.messaging;

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
