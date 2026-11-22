package com.rhitam.jobtracker.outbox;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "outbox_events")
public class OutboxEvent {

    public enum Status { PENDING, PROCESSED, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID userId;
    private String aggregateType;
    private UUID aggregateId;
    private String eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> payload;

    @Enumerated(EnumType.STRING)
    private Status status;

    private int attempts;
    private String lastError;
    private Instant createdAt;
    private Instant processedAt;

    protected OutboxEvent() {
    }

    public OutboxEvent(UUID userId, String aggregateType, UUID aggregateId, String eventType,
                       Map<String, Object> payload, Instant now) {
        this.userId = userId;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.status = Status.PENDING;
        this.createdAt = now;
    }

    public void markProcessed(Instant now) {
        this.status = Status.PROCESSED;
        this.processedAt = now;
        this.lastError = null;
    }

    /** Stays PENDING (and is retried on the next poll) until maxAttempts is reached. */
    public void recordFailure(String error, int maxAttempts) {
        this.attempts++;
        this.lastError = error;
        if (attempts >= maxAttempts) {
            this.status = Status.FAILED;
        }
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getAggregateType() { return aggregateType; }
    public UUID getAggregateId() { return aggregateId; }
    public String getEventType() { return eventType; }
    public Map<String, Object> getPayload() { return payload; }
    public Status getStatus() { return status; }
    public int getAttempts() { return attempts; }
    public String getLastError() { return lastError; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getProcessedAt() { return processedAt; }
}
