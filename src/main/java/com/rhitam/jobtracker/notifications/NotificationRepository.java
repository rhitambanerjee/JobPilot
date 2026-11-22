package com.rhitam.jobtracker.notifications;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    List<Notification> findTop100ByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<Notification> findByIdAndUserId(UUID id, UUID userId);

    /** Background email delivery: spans all users on purpose (each row already carries its own recipient). */
    @Query(value = """
            SELECT * FROM notifications
            WHERE email_status = 'PENDING'
            ORDER BY created_at
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<Notification> lockPendingEmails(@Param("limit") int limit);

    @Modifying
    @Query("update Notification n set n.readAt = :now where n.readAt is null and n.userId = :userId")
    int markAllRead(@Param("userId") UUID userId, @Param("now") Instant now);
}
