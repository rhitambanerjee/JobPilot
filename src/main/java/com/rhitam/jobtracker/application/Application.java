package com.rhitam.jobtracker.application;

import com.rhitam.jobtracker.common.exception.InvalidTransitionException;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/** Aggregate root. All stage changes go through moveTo() so the state machine can't be bypassed. */
@Entity
@Table(name = "applications")
public class Application {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String company;
    private String role;
    private String jobUrl;
    private String contactEmail;
    private UUID userId;

    @Enumerated(EnumType.STRING)
    private Stage stage;

    private String notes;
    private Instant lastActivityAt;
    private Instant staleNotifiedAt;
    private Instant createdAt;
    private Instant updatedAt;

    @Version
    private Long version;

    protected Application() {
    }

    public Application(UUID userId, String company, String role, String jobUrl, String notes, Stage stage, Instant now) {
        this.userId = userId;
        this.company = company;
        this.role = role;
        this.jobUrl = jobUrl;
        this.notes = notes;
        this.stage = stage;
        this.lastActivityAt = now;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** @return the previous stage */
    public Stage moveTo(Stage next, Instant now) {
        if (!stage.canTransitionTo(next)) {
            throw new InvalidTransitionException(
                    "Cannot move from " + stage + " to " + next + ". Allowed: " + stage.allowedNext());
        }
        Stage previous = stage;
        this.stage = next;
        this.lastActivityAt = now;
        this.updatedAt = now;
        return previous;
    }

    public void markStaleNotified(Instant now) {
        this.staleNotifiedAt = now;
    }

    /** Does NOT touch lastActivityAt: adding an address must not reset the idle clock. */
    public void updateContactEmail(String email, Instant now) {
        this.contactEmail = email;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getCompany() { return company; }
    public String getRole() { return role; }
    public String getJobUrl() { return jobUrl; }
    public String getContactEmail() { return contactEmail; }
    public Stage getStage() { return stage; }
    public String getNotes() { return notes; }
    public Instant getLastActivityAt() { return lastActivityAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
