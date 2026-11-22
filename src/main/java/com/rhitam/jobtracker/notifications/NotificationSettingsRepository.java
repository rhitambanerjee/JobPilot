package com.rhitam.jobtracker.notifications;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationSettingsRepository extends JpaRepository<NotificationSettings, UUID> {
}
