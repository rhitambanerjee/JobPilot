package com.rhitam.jobtracker.actions.reminder;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reminders")
public class Reminder {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID applicationId;
    private String message;
    private Instant dueAt;
    private boolean done;
    private Instant createdAt;

    protected Reminder() {
    }

    public Reminder(UUID applicationId, String message, Instant dueAt, Instant now) {
        this.applicationId = applicationId;
        this.message = message;
        this.dueAt = dueAt;
        this.createdAt = now;
    }

    public void complete() {
        this.done = true;
    }

    public UUID getId() { return id; }
    public UUID getApplicationId() { return applicationId; }
    public String getMessage() { return message; }
    public Instant getDueAt() { return dueAt; }
    public boolean isDone() { return done; }
    public Instant getCreatedAt() { return createdAt; }
}
