package com.rhitam.jobtracker.jobs;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A unit of work produced by a rule. Rows are inserted by JobQClient (native SQL, ON CONFLICT DO NOTHING)
 * and only ever updated through the entity afterwards.
 */
@Entity
@Table(name = "rule_executions")
public class RuleExecution {

    public enum Status { PENDING, RUNNING, SUCCEEDED, FAILED, SKIPPED }

    @Id
    private UUID id;

    private String idempotencyKey;
    private UUID ruleId;
    private UUID applicationId;
    private String actionType;

    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> params;

    @Enumerated(EnumType.STRING)
    private Status status;

    private int attempts;
    private int maxAttempts;
    private Instant nextRunAt;
    private Instant lockedAt;
    private String lastError;
    private Instant createdAt;
    private Instant updatedAt;

    protected RuleExecution() {
    }

    public void markRunning(Instant now) {
        this.status = Status.RUNNING;
        this.attempts++;
        this.lockedAt = now;
        this.updatedAt = now;
    }

    public void markSucceeded(Instant now) {
        this.status = Status.SUCCEEDED;
        this.lockedAt = null;
        this.lastError = null;
        this.updatedAt = now;
    }

    /** Parked, not failed: attempts reset so JobQClient can revive it when the same event situation recurs. */
    public void markSkipped(String reason, Instant now) {
        this.status = Status.SKIPPED;
        this.attempts = 0;
        this.lockedAt = null;
        this.lastError = reason;
        this.updatedAt = now;
    }

    public void markExhausted(String reason, Instant now) {
        this.status = Status.FAILED;
        this.lockedAt = null;
        this.lastError = reason;
        this.updatedAt = now;
    }

    /** Back to PENDING with a delay, or FAILED once attempts are used up. */
    public void recordFailure(String error, Instant now, Duration backoff) {
        this.lastError = error;
        this.lockedAt = null;
        this.updatedAt = now;
        if (attempts >= maxAttempts) {
            this.status = Status.FAILED;
        } else {
            this.status = Status.PENDING;
            this.nextRunAt = now.plus(backoff);
        }
    }

    public UUID getId() { return id; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public UUID getRuleId() { return ruleId; }
    public UUID getApplicationId() { return applicationId; }
    public String getActionType() { return actionType; }
    public Map<String, Object> getParams() { return params; }
    public Status getStatus() { return status; }
    public int getAttempts() { return attempts; }
    public int getMaxAttempts() { return maxAttempts; }
    public Instant getNextRunAt() { return nextRunAt; }
    public String getLastError() { return lastError; }
}
