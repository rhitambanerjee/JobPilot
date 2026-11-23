package com.rhitam.jobtracker.auth;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String email;
    private String passwordHash;
    private String displayName;
    private Instant createdAt;
    private boolean emailVerified;
    private int sessionVersion;

    protected User() {
    }

    public User(String email, String passwordHash, String displayName, Instant now) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.createdAt = now;
    }

    public void markVerified() {
        this.emailVerified = true;
    }

    /** Also bumps sessionVersion, which invalidates every login session this user currently has. */
    public void changePassword(String newPasswordHash) {
        this.passwordHash = newPasswordHash;
        this.sessionVersion++;
    }

    public UUID getId() { return id; }
    public boolean isEmailVerified() { return emailVerified; }
    public int getSessionVersion() { return sessionVersion; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getDisplayName() { return displayName; }
    public Instant getCreatedAt() { return createdAt; }
}
