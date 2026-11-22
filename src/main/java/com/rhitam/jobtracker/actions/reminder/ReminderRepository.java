package com.rhitam.jobtracker.actions.reminder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReminderRepository extends JpaRepository<Reminder, UUID> {

    List<Reminder> findByApplicationId(UUID applicationId);

    List<Reminder> findByDoneFalseAndDueAtBeforeOrderByDueAt(Instant cutoff);

    List<Reminder> findByDoneFalseOrderByDueAt();
}
