package com.rhitam.jobtracker.outbox;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {

    /**
     * Claims a batch of pending events. FOR UPDATE SKIP LOCKED means several app instances can poll
     * concurrently: each grabs different rows and nobody blocks on someone else's batch.
     * Must be called inside a transaction (the row locks live until it ends).
     */
    @Query(value = """
            SELECT * FROM outbox_events
            WHERE status = 'PENDING'
            ORDER BY created_at
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEvent> lockPendingBatch(@Param("limit") int limit);

    long countByStatus(OutboxEvent.Status status);
}
