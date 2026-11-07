package com.flashsale.simulation;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "simulation_runs")
public class SimulationRun {
    @Id
    private String id;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String configuration; // JSON

    private String status;
    private Integer totalRequests;
    private Integer successfulReservations;
    private Integer rejectedRequests;
    private Integer failedRequests;
    private Long durationMs;
    private BigDecimal throughput;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
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
    public BigDecimal getThroughput() { return throughput; } public void setThroughput(BigDecimal throughput) { this.throughput = throughput; }
    public String getLatencyStatistics() { return latencyStatistics; } public void setLatencyStatistics(String latencyStatistics) { this.latencyStatistics = latencyStatistics; }
    public OffsetDateTime getCreatedAt() { return createdAt; } public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getCompletedAt() { return completedAt; } public void setCompletedAt(OffsetDateTime completedAt) { this.completedAt = completedAt; }
}
