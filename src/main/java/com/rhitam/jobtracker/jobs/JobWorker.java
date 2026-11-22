package com.rhitam.jobtracker.jobs;

import com.rhitam.jobtracker.actions.ActionContext;
import com.rhitam.jobtracker.actions.ActionRegistry;
import com.rhitam.jobtracker.actions.ActionSkippedException;
import com.rhitam.jobtracker.actions.NonRetryableActionException;
import com.rhitam.jobtracker.notifications.NotificationService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Three short transactions per job, never one long one:
 *  1. claim:   lock rows (SKIP LOCKED), mark RUNNING, commit  -> other workers can't take them
 *  2. execute: run the handler AND mark SUCCEEDED in the same transaction, so the side effect and the
 *              "done" flag commit atomically (no duplicate side effects after a crash)
 *  3. on failure: the handler's work rolled back; record the error + backoff in a fresh transaction
 */
@Component
public class JobWorker {

    private static final Logger log = LoggerFactory.getLogger(JobWorker.class);

    private final RuleExecutionRepository repository;
    private final ActionRegistry registry;
    private final NotificationService notifications;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final int batchSize;
    private final Duration lockTimeout;

    public JobWorker(RuleExecutionRepository repository, ActionRegistry registry, NotificationService notifications,
                     TransactionTemplate tx, Clock clock,
                     @Value("${tracker.jobs.batch-size:20}") int batchSize,
                     @Value("${tracker.jobs.lock-timeout-seconds:300}") long lockTimeoutSeconds) {
        this.repository = repository;
        this.registry = registry;
        this.notifications = notifications;
        this.tx = tx;
        this.clock = clock;
        this.batchSize = batchSize;
        this.lockTimeout = Duration.ofSeconds(lockTimeoutSeconds);
    }

    @Scheduled(fixedDelayString = "${tracker.jobs.poll-ms:1000}")
    public void scheduledRun() {
        runOnce();
    }

    /** @return number of jobs claimed in this pass */
    public int runOnce() {
        List<UUID> claimed = claim();
        claimed.forEach(this::process);
        return claimed.size();
    }

    private List<UUID> claim() {
        List<UUID> ids = tx.execute(status -> {
            Instant now = clock.instant();
            List<UUID> runnable = new ArrayList<>();
            for (RuleExecution job : repository.claim(now, now.minus(lockTimeout), batchSize)) {
                if (job.getAttempts() >= job.getMaxAttempts()) {
                    // reclaimed after a crash but already out of attempts
                    job.markExhausted("Exceeded max attempts (worker likely crashed mid-run)", now);
                } else {
                    job.markRunning(now);
                    runnable.add(job.getId());
                }
            }
            return runnable;
        });
        return ids == null ? List.of() : ids;
    }

    private void process(UUID id) {
        try {
            tx.executeWithoutResult(status -> {
                RuleExecution job = repository.findById(id).orElseThrow();
                if (job.getStatus() != RuleExecution.Status.RUNNING) {
                    return;
                }
                registry.get(job.getActionType())
                        .execute(new ActionContext(job.getApplicationId(), job.getRuleId(), job.getParams()));
                // same transaction as the action: the notification exists if and only if the action committed
                notifications.recordRuleRun(job.getRuleId(), job.getApplicationId(), job.getActionType());
                job.markSucceeded(clock.instant());
            });
        } catch (ActionSkippedException e) {
            // precondition missing (e.g. no contact email yet): park it, tell the user once, revive on a later event
            tx.executeWithoutResult(status -> repository.findById(id).ifPresent(job -> {
                boolean firstTime = job.getLastError() == null;
                job.markSkipped(e.getMessage(), clock.instant());
                if (firstTime) {
                    notifications.recordProblem(job.getRuleId(), job.getApplicationId(), e.getMessage());
                }
            }));
        } catch (NonRetryableActionException e) {
            // can never work as configured: fail now instead of burning retries
            tx.executeWithoutResult(status -> repository.findById(id).ifPresent(job -> {
                job.markExhausted(e.getMessage(), clock.instant());
                notifications.recordProblem(job.getRuleId(), job.getApplicationId(), e.getMessage());
            }));
        } catch (RuntimeException e) {
            log.warn("Job {} failed: {}", id, e.toString());
            tx.executeWithoutResult(status -> repository.findById(id).ifPresent(job ->
                    job.recordFailure(e.getClass().getSimpleName() + ": " + e.getMessage(), clock.instant(),
                            backoff(job.getAttempts()))));
        }
    }

    /** 10s, 20s, 40s ... capped at 5 minutes. */
    static Duration backoff(int attempts) {
        long seconds = Math.min(300L, 5L << Math.min(Math.max(attempts, 1), 6));
        return Duration.ofSeconds(seconds);
    }
}
