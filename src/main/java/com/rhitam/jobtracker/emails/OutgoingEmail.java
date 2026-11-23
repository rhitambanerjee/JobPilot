package com.rhitam.jobtracker.emails;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** An email to a third party (HR / careers). Written by the rule action, delivered later by OutgoingEmailSender. */
@Entity
@Table(name = "outgoing_emails")
public class OutgoingEmail {

    public enum Status { PENDING, SENT, FAILED }

    /** FOLLOW_UP = a rule emailing HR for an application; ACCOUNT = verification / password-reset mail. */
    public enum Kind { FOLLOW_UP, ACCOUNT }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID userId;
    private UUID applicationId;
    private UUID ruleId;
    private String toAddress;
    private String subject;
    private String body;

    @Enumerated(EnumType.STRING)
    private Status status;

    @Enumerated(EnumType.STRING)
    private Kind kind;

    private int attempts;
    private String lastError;
    private Instant createdAt;
    private Instant sentAt;

    protected OutgoingEmail() {
    }

    public OutgoingEmail(UUID userId, UUID applicationId, UUID ruleId, String toAddress, String subject, String body, Instant now) {
        this.userId = userId;
        this.applicationId = applicationId;
        this.ruleId = ruleId;
        this.toAddress = toAddress;
        this.subject = subject;
        this.body = body;
        this.status = Status.PENDING;
        this.kind = Kind.FOLLOW_UP;
        this.createdAt = now;
    }

    /** An account email has no application and no rule. */
    public static OutgoingEmail account(UUID userId, String toAddress, String subject, String body, Instant now) {
        OutgoingEmail email = new OutgoingEmail(userId, null, null, toAddress, subject, body, now);
        email.kind = Kind.ACCOUNT;
        return email;
    }

    public void markSent(Instant now) {
        this.status = Status.SENT;
        this.attempts++;
        this.lastError = null;
        this.sentAt = now;
    }

    /** Stays PENDING (retried on the next poll) until maxAttempts is reached. */
    public void recordFailure(String error, int maxAttempts) {
        this.attempts++;
        this.lastError = error;
        if (attempts >= maxAttempts) {
            this.status = Status.FAILED;
        }
    }

    public void failPermanently(String reason) {
        this.status = Status.FAILED;
        this.lastError = reason;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public UUID getApplicationId() { return applicationId; }
    public UUID getRuleId() { return ruleId; }
    public String getToAddress() { return toAddress; }
    public String getSubject() { return subject; }
    public String getBody() { return body; }
    public Status getStatus() { return status; }
    public Kind getKind() { return kind; }
    public int getAttempts() { return attempts; }
    public String getLastError() { return lastError; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getSentAt() { return sentAt; }
}
