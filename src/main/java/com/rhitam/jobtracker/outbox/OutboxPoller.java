package com.rhitam.jobtracker.outbox;

import com.rhitam.jobtracker.rules.RuleEngine;
import java.time.Clock;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Relays outbox events to the rule engine. Delivery is at-least-once: if the process dies after the
 * rule engine ran but before commit, the event is re-delivered. That is safe because the rule engine's
 * output is keyed by an idempotency key (see JobQClient).
 */
@Component
public class OutboxPoller {

    private static final Logger log = LoggerFactory.getLogger(OutboxPoller.class);

    private final OutboxRepository repository;
    private final RuleEngine ruleEngine;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final int batchSize;
    private final int maxAttempts;

    public OutboxPoller(OutboxRepository repository, RuleEngine ruleEngine, TransactionTemplate tx, Clock clock,
                        @Value("${tracker.outbox.batch-size:50}") int batchSize,
                        @Value("${tracker.outbox.max-attempts:5}") int maxAttempts) {
        this.repository = repository;
        this.ruleEngine = ruleEngine;
        this.tx = tx;
        this.clock = clock;
        this.batchSize = batchSize;
        this.maxAttempts = maxAttempts;
    }

    @Scheduled(fixedDelayString = "${tracker.outbox.poll-ms:1000}")
    public void scheduledPoll() {
        poll();
    }

    /** @return number of events handled in this batch */
    public int poll() {
        Integer handled = tx.execute(status -> {
            List<OutboxEvent> batch = repository.lockPendingBatch(batchSize);
            for (OutboxEvent event : batch) {
                try {
                    ruleEngine.handle(event);
                    event.markProcessed(clock.instant());
                } catch (RuntimeException e) {
                    log.warn("Outbox event {} ({}) failed: {}", event.getId(), event.getEventType(), e.getMessage());
                    event.recordFailure(e.getMessage(), maxAttempts);
                }
            }
            return batch.size();
        });
        return handled == null ? 0 : handled;
    }
}
