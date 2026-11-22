package com.rhitam.jobtracker.actions;

import com.rhitam.jobtracker.actions.reminder.Reminder;
import com.rhitam.jobtracker.actions.reminder.ReminderRepository;
import com.rhitam.jobtracker.common.exception.BadRequestException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** params: message (string, default "Follow up"), daysFromNow (number >= 0, default 3). */
@Component
public class CreateReminderHandler implements ActionHandler {

    public static final String TYPE = "create_reminder";

    private final ReminderRepository repository;
    private final Clock clock;

    public CreateReminderHandler(ReminderRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public void validate(UUID userId, Map<String, Object> params) {
        Object days = params.get("daysFromNow");
        if (days != null && !(days instanceof Number n && n.intValue() >= 0)) {
            throw new BadRequestException("create_reminder: 'daysFromNow' must be a number >= 0");
        }
    }

    @Override
    public void execute(ActionContext context) {
        Map<String, Object> params = context.params();
        String message = params.get("message") instanceof String m && !m.isBlank() ? m : "Follow up";
        int days = params.get("daysFromNow") instanceof Number n ? n.intValue() : 3;
        Instant now = clock.instant();
        repository.save(new Reminder(context.userId(), context.applicationId(), message, now.plus(Duration.ofDays(days)), now));
    }
}
