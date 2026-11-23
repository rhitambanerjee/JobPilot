package com.rhitam.jobtracker.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * "At most N per hour per key". Used so the resend-verification and forgot-password forms can't be used to flood
 * someone's inbox. In memory and per instance, like LoginThrottle.
 */
@Component
public class RequestLimiter {

    private final Map<String, Deque<Instant>> hits = new ConcurrentHashMap<>();
    private final Clock clock;
    private final int max;
    private final Duration window = Duration.ofHours(1);

    public RequestLimiter(Clock clock, @Value("${tracker.auth.max-emails-per-hour:3}") int max) {
        this.clock = clock;
        this.max = max;
    }

    /** @return true and records the attempt if the key is under its limit; false if it is over */
    public boolean tryAcquire(String key) {
        Deque<Instant> recent = hits.computeIfAbsent(key, k -> new ConcurrentLinkedDeque<>());
        Instant now = clock.instant();
        recent.removeIf(t -> t.isBefore(now.minus(window)));
        if (recent.size() >= max) {
            return false;
        }
        recent.add(now);
        return true;
    }
}
