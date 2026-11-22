package com.rhitam.jobtracker.audit;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Append-only record of who did what. Never updated after insert. */
@Entity
@Table(name = "audit_logs")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String entityType;
    private UUID entityId;
    private String action;
    private String actor;

    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> details;

    private Instant createdAt;

    protected AuditLog() {
    }

    public static AuditLog of(String entityType, UUID entityId, String action, String actor,
                              Map<String, Object> details, Instant now) {
        AuditLog log = new AuditLog();
        log.entityType = entityType;
        log.entityId = entityId;
        log.action = action;
        log.actor = actor;
        log.details = details;
        log.createdAt = now;
        return log;
    }

    public UUID getId() { return id; }
    public String getEntityType() { return entityType; }
    public UUID getEntityId() { return entityId; }
    public String getAction() { return action; }
    public String getActor() { return actor; }
    public Map<String, Object> getDetails() { return details; }
    public Instant getCreatedAt() { return createdAt; }
}
