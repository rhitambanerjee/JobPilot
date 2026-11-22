package com.rhitam.jobtracker.notifications;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * One in-app notification, which doubles as the email outbox: when the user has opted in,
 * emailStatus starts as PENDING and NotificationEmailSender delivers it later.
 */
@Entity
@Table(name = "notifications")
public class Notification {

    public enum EmailStatus { NONE, PENDING, SENT, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID userId;
    private UUID applicationId;
    private UUID ruleId;
    private String title;
    private String message;
    private Instant readAt;
    private String emailTo;

    @Enumerated(EnumType.STRING)
    private EmailStatus emailStatus;

    private int emailAttempts;
    private String emailError;
    private Instant createdAt;
    private Instant emailedAt;

    protected Notification() {
    }

    /** @param emailTo recipient, or null when the user has not opted in to email */
    public Notification(UUID userId, UUID applicationId, UUID ruleId, String title, String message, String emailTo, Instant now) {
        this.userId = userId;
        this.applicationId = applicationId;
        this.ruleId = ruleId;
        this.title = title;
        this.message = message;
        this.emailTo = emailTo;
        this.emailStatus = emailTo == null ? EmailStatus.NONE : EmailStatus.PENDING;
        this.createdAt = now;
    }

    public void markRead(Instant now) {
        if (readAt == null) {
            readAt = now;
        }
    }

    public void markEmailSent(Instant now) {
        this.emailStatus = EmailStatus.SENT;
        this.emailAttempts++;
        this.emailError = null;
        this.emailedAt = now;
    }

    /** Stays PENDING (retried on the next poll) until maxAttempts is reached. */
    public void recordEmailFailure(String error, int maxAttempts) {
        this.emailAttempts++;
        this.emailError = error;
        if (emailAttempts >= maxAttempts) {
            this.emailStatus = EmailStatus.FAILED;
        }
    }

    public void failEmail(String reason) {
        this.emailStatus = EmailStatus.FAILED;
        this.emailError = reason;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public UUID getApplicationId() { return applicationId; }
    public UUID getRuleId() { return ruleId; }
    public String getTitle() { return title; }
    public String getMessage() { return message; }
    public Instant getReadAt() { return readAt; }
    public String getEmailTo() { return emailTo; }
    public EmailStatus getEmailStatus() { return emailStatus; }
    public int getEmailAttempts() { return emailAttempts; }
    public String getEmailError() { return emailError; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getEmailedAt() { return emailedAt; }
}
