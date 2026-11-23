package com.rhitam.jobtracker.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * One-time tokens for the links in verification and password-reset emails.
 *  - 256 random bits, so they can't be guessed
 *  - only the SHA-256 hash is stored: someone who reads the database can't use the links
 *  - consuming a token is ONE atomic UPDATE ... RETURNING, so two clicks can't both succeed
 *  - issuing a new token of a type replaces the old one, so only the latest link works
 */
@Service
public class AuthTokenService {

    public enum Type { VERIFY_EMAIL, RESET_PASSWORD }

    private static final SecureRandom RANDOM = new SecureRandom();

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public AuthTokenService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** @return the raw token. It exists only in the email; the database keeps just its hash. */
    public String issue(UUID userId, Type type, Duration ttl) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant now = clock.instant();
        invalidateAll(userId, type);
        jdbc.update("""
                INSERT INTO auth_tokens (id, user_id, type, token_hash, expires_at, created_at)
                VALUES (?, ?, ?, ?, ?, ?)""",
                UUID.randomUUID(), userId, type.name(), hash(raw), Timestamp.from(now.plus(ttl)), Timestamp.from(now));
        return raw;
    }

    /** Marks the token used and returns its owner, or empty if it is unknown, expired, already used or the wrong type. */
    public Optional<UUID> consume(String rawToken, Type type) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        Timestamp now = Timestamp.from(clock.instant());
        List<UUID> owners = jdbc.query("""
                UPDATE auth_tokens SET used_at = ?
                WHERE token_hash = ? AND type = ? AND used_at IS NULL AND expires_at > ?
                RETURNING user_id""",
                (rs, i) -> rs.getObject("user_id", UUID.class),
                now, hash(rawToken.trim()), type.name(), now);
        return owners.stream().findFirst();
    }

    public void invalidateAll(UUID userId, Type type) {
        jdbc.update("DELETE FROM auth_tokens WHERE user_id = ? AND type = ?", userId, type.name());
    }

    static String hash(String raw) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(sha.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }
}
