package com.rhitam.jobtracker.actions.reminder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReminderRepository extends JpaRepository<Reminder, UUID> {

    Optional<Reminder> findByIdAndUserId(UUID id, UUID userId);

    List<Reminder> findByApplicationIdAndUserId(UUID applicationId, UUID userId);

    List<Reminder> findByUserIdAndDoneFalseAndDueAtBeforeOrderByDueAt(UUID userId, Instant cutoff);

    List<Reminder> findByUserIdAndDoneFalseOrderByDueAt(UUID userId);
}
