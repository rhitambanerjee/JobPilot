package com.rhitam.jobtracker.outbox;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes an event row in the caller's transaction. MANDATORY makes it impossible to publish an event
 * outside a business transaction (which would defeat the guarantee).
 */
@Component
public class OutboxPublisher {

    private final OutboxRepository repository;
    private final Clock clock;

    public OutboxPublisher(OutboxRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(String aggregateType, UUID aggregateId, String eventType, Map<String, Object> payload) {
        repository.save(new OutboxEvent(aggregateType, aggregateId, eventType, payload, clock.instant()));
    }
}
