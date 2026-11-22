package com.rhitam.jobtracker.emails;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutgoingEmailRepository extends JpaRepository<OutgoingEmail, UUID> {

    List<OutgoingEmail> findByApplicationIdOrderByCreatedAtDesc(UUID applicationId);

    /** SKIP LOCKED batch, same pattern as the outbox. Call inside a transaction. */
    @Query(value = """
            SELECT * FROM outgoing_emails
            WHERE status = 'PENDING'
            ORDER BY created_at
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutgoingEmail> lockPending(@Param("limit") int limit);
}
