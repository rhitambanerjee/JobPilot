package com.rhitam.jobtracker.auth;

import com.rhitam.jobtracker.common.exception.TooManyAttemptsException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Slows down password guessing: after maxFailures wrong passwords for one email, further logins for that email
 * are refused until the oldest failure falls out of the window. In memory, so it resets on restart and is per
 * instance. Good enough for a single-node app; use a shared store if you ever run several.
 */
@Component
public class LoginThrottle {

    private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();
    private final Clock clock;
    private final int maxFailures;
    private final Duration window;

    public LoginThrottle(Clock clock,
                         @Value("${tracker.auth.max-failed-logins:5}") int maxFailures,
                         @Value("${tracker.auth.lockout-minutes:15}") long lockoutMinutes) {
        this.clock = clock;
        this.maxFailures = maxFailures;
        this.window = Duration.ofMinutes(lockoutMinutes);
    }

    public void checkAllowed(String email) {
        Deque<Instant> recent = failures.get(key(email));
        if (recent == null) {
            return;
        }
        Instant cutoff = clock.instant().minus(window);
        recent.removeIf(t -> t.isBefore(cutoff));
        if (recent.size() >= maxFailures) {
            throw new TooManyAttemptsException(
                    "Too many failed attempts. Try again in " + window.toMinutes() + " minutes.");
        }
    }

    public void recordFailure(String email) {
        failures.computeIfAbsent(key(email), k -> new ConcurrentLinkedDeque<>()).add(clock.instant());
    }

    public void reset(String email) {
        failures.remove(key(email));
    }

    private static String key(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
