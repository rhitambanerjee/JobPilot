package com.rhitam.jobtracker.emails;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "email_templates")
public class EmailTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID userId;   // null for the built-in templates everyone shares
    private String name;
    private String subject;
    private String body;
    private boolean builtIn;
    private Instant createdAt;
    private Instant updatedAt;

    protected EmailTemplate() {
    }

    /** User-saved template (built-ins are only created by the migration). */
    public EmailTemplate(UUID userId, String name, String subject, String body, Instant now) {
        this.userId = userId;
        this.name = name;
        this.subject = subject;
        this.body = body;
        this.builtIn = false;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(String name, String subject, String body, Instant now) {
        this.name = name;
        this.subject = subject;
        this.body = body;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getName() { return name; }
    public String getSubject() { return subject; }
    public String getBody() { return body; }
    public boolean isBuiltIn() { return builtIn; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
