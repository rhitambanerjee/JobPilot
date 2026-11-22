package com.rhitam.jobtracker.actions.checklist;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "checklists")
public class Checklist {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID userId;
    private UUID applicationId;
    private String title;

    @JdbcTypeCode(SqlTypes.JSON)
    private List<String> items;

    private Instant createdAt;

    protected Checklist() {
    }

    public Checklist(UUID userId, UUID applicationId, String title, List<String> items, Instant now) {
        this.userId = userId;
        this.applicationId = applicationId;
        this.title = title;
        this.items = items;
        this.createdAt = now;
    }

    public UUID getId() { return id; }
    public UUID getApplicationId() { return applicationId; }
    public String getTitle() { return title; }
    public List<String> getItems() { return items; }
    public Instant getCreatedAt() { return createdAt; }
}
