package com.rhitam.jobtracker.auth;

import com.rhitam.jobtracker.common.exception.BadRequestException;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    /** Owner of rows created before accounts existed (see V8). */
    public static final UUID LEGACY_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    /** Tables with a user_id column that the legacy owner's rows are moved out of. Constants, never user input. */
    private static final List<String> OWNED_TABLES = List.of(
            "applications", "audit_logs", "outbox_events", "rules", "rule_executions", "checklists",
            "reminders", "notifications", "outgoing_emails", "email_templates", "notification_settings");

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public UserService(UserRepository users, PasswordEncoder encoder, JdbcTemplate jdbc, Clock clock) {
        this.users = users;
        this.encoder = encoder;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional
    public User register(String email, String rawPassword, String displayName) {
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        if (users.existsByEmailIgnoreCase(normalized)) {
            throw new BadRequestException("An account with this email already exists");
        }
        String name = displayName == null || displayName.isBlank() ? null : displayName.trim();
        User created;
        try {
            created = users.saveAndFlush(new User(normalized, encoder.encode(rawPassword), name, clock.instant()));
        } catch (DataIntegrityViolationException e) {   // two people registering the same email at once
            throw new BadRequestException("An account with this email already exists");
        }
        claimExistingData(created);
        return created;
    }

    /** The very first account inherits everything that was created before accounts existed. */
    private void claimExistingData(User first) {
        boolean onlyPlaceholderBefore = users.existsById(LEGACY_USER_ID) && users.count() == 2;
        if (!onlyPlaceholderBefore) {
            return;
        }
        for (String table : OWNED_TABLES) {
            jdbc.update("UPDATE " + table + " SET user_id = ? WHERE user_id = ?", first.getId(), LEGACY_USER_ID);
        }
        jdbc.update("DELETE FROM users WHERE id = ?", LEGACY_USER_ID);
    }
}
