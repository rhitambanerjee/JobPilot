package com.rhitam.jobtracker.auth;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.rhitam.jobtracker.common.exception.TooManyAttemptsException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class LoginThrottleTest {

    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-01-01T00:00:00Z"));

    private final Clock clock = new Clock() {
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now.get(); }
    };

    @Test
    void locksOutAfterTooManyFailures_ignoringCase_andResetsOnSuccess() {
        LoginThrottle throttle = new LoginThrottle(clock, 3, 15);
        for (int i = 0; i < 3; i++) {
            throttle.recordFailure("Alice@Example.com");
        }
        assertThrows(TooManyAttemptsException.class, () -> throttle.checkAllowed("alice@example.com"));

        throttle.reset("alice@example.com");
        assertDoesNotThrow(() -> throttle.checkAllowed("alice@example.com"));
    }

    @Test
    void failuresExpireAfterTheLockoutWindow() {
        LoginThrottle throttle = new LoginThrottle(clock, 2, 15);
        throttle.recordFailure("bob@example.com");
        throttle.recordFailure("bob@example.com");
        assertThrows(TooManyAttemptsException.class, () -> throttle.checkAllowed("bob@example.com"));

        now.set(now.get().plus(Duration.ofMinutes(16)));
        assertDoesNotThrow(() -> throttle.checkAllowed("bob@example.com"));
    }

    @Test
    void oneUsersFailuresDoNotLockAnotherUser() {
        LoginThrottle throttle = new LoginThrottle(clock, 1, 15);
        throttle.recordFailure("bob@example.com");
        assertDoesNotThrow(() -> throttle.checkAllowed("carol@example.com"));
    }
}
