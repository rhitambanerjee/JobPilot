package com.rhitam.jobtracker.jobs;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * The seam between this app and the queue. Right now the "queue" is the rule_executions table in the same
 * Postgres. The method signature is all RuleEngine knows, so swapping this for your JobQ library/HTTP
 * client later only touches this class (and JobWorker's claim/ack side).
 *
 * Idempotency: the UNIQUE(idempotency_key) constraint means enqueueing the same key twice stores one job.
 * The one exception: a job parked as SKIPPED (precondition missing) is revived by a repeat enqueue. That is what makes at-least-once event delivery safe.
 */
@Component
public class JobQClient {

    private static final String INSERT = """
            INSERT INTO rule_executions
                (id, idempotency_key, rule_id, user_id, application_id, action_type, params,
                 status, attempts, max_attempts, next_run_at, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, 'PENDING', 0, ?, ?, ?, ?)
            ON CONFLICT (idempotency_key) DO UPDATE
                SET status = 'PENDING', next_run_at = EXCLUDED.next_run_at, updated_at = EXCLUDED.updated_at
                WHERE rule_executions.status = 'SKIPPED'
            """;

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final int maxAttempts;

    public JobQClient(JdbcTemplate jdbc, ObjectMapper mapper, Clock clock,
                      @Value("${tracker.jobs.max-attempts:5}") int maxAttempts) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.clock = clock;
        this.maxAttempts = maxAttempts;
    }

    /** @return true if a job will run (newly stored, or a SKIPPED one revived); false if one already exists */
    public boolean enqueue(JobRequest request) {
        Timestamp now = Timestamp.from(clock.instant());
        int inserted = jdbc.update(INSERT,
                UUID.randomUUID(),
                request.idempotencyKey(),
                request.ruleId(),
                request.userId(),
                request.applicationId(),
                request.actionType(),
                toJson(request),
                maxAttempts,
                now, now, now);
        return inserted == 1;
    }

    private String toJson(JobRequest request) {
        try {
            return mapper.writeValueAsString(request.params());
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Job params are not serialisable", e);
        }
    }
}
