package com.rhitam.jobtracker.notifications;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** One row per user: where to send email and whether they agreed to it. */
@Entity
@Table(name = "notification_settings")
public class NotificationSettings {

    @Id
    private UUID userId;

    private String email;
    private boolean emailEnabled;
    private Instant updatedAt;

    protected NotificationSettings() {
    }

    public NotificationSettings(UUID userId, String email, boolean emailEnabled, Instant now) {
        this.userId = userId;
        update(email, emailEnabled, now);
    }

    public final void update(String email, boolean emailEnabled, Instant now) {
        this.email = email;
        this.emailEnabled = emailEnabled;
        this.updatedAt = now;
    }

    /** True only when the user opted in AND gave an address. */
    public boolean canEmail() {
        return emailEnabled && email != null && !email.isBlank();
    }

    public UUID getUserId() { return userId; }
    public String getEmail() { return email; }
    public boolean isEmailEnabled() { return emailEnabled; }
    public Instant getUpdatedAt() { return updatedAt; }
}
