package com.rhitam.jobtracker.jobs;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.rhitam.jobtracker.PostgresIT;
import com.rhitam.jobtracker.actions.reminder.ReminderRepository;
import com.rhitam.jobtracker.application.Application;
import com.rhitam.jobtracker.application.ApplicationService;
import com.rhitam.jobtracker.application.dto.CreateApplicationRequest;
import com.rhitam.jobtracker.notifications.NotificationRepository;
import com.rhitam.jobtracker.outbox.EventTypes;
import com.rhitam.jobtracker.outbox.OutboxEvent;
import com.rhitam.jobtracker.outbox.OutboxRepository;
import com.rhitam.jobtracker.rules.Rule;
import com.rhitam.jobtracker.rules.RuleEngine;
import com.rhitam.jobtracker.rules.RuleRepository;
import com.rhitam.jobtracker.rules.model.ActionSpec;
import com.rhitam.jobtracker.rules.model.Trigger;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** The property the whole design exists for: duplicate events -> exactly one side effect. */
class IdempotencyIT extends PostgresIT {

    @Autowired ApplicationService applications;
    @Autowired OutboxRepository outbox;
    @Autowired RuleRepository rules;
    @Autowired RuleEngine ruleEngine;
    @Autowired JobWorker worker;
    @Autowired RuleExecutionRepository executions;
    @Autowired ReminderRepository reminders;
    @Autowired NotificationRepository notifications;

    @Test
    void deliveringTheSameEventTwiceProducesOneJobAndOneReminder() {
        rules.save(new Rule("follow up", new Trigger(EventTypes.APPLICATION_CREATED), List.of(),
                List.of(new ActionSpec("create_reminder", Map.of("message", "Follow up", "daysFromNow", 7))),
                true, Instant.now()));
        Application app = applications.create(new CreateApplicationRequest("Rippling", "Backend Engineer", null, null, null, null));
        OutboxEvent event = outbox.findAll().get(0);

        assertEquals(1, ruleEngine.handle(event));   // first delivery enqueues
        assertEquals(0, ruleEngine.handle(event));   // redelivery is a no-op
        assertEquals(1, executions.countByApplicationId(app.getId()));

        worker.runOnce();
        worker.runOnce();                            // nothing left to claim
        assertEquals(1, reminders.findByApplicationId(app.getId()).size());
        assertEquals(1, executions.countByStatus(RuleExecution.Status.SUCCEEDED));
        assertEquals(1, notifications.count());      // one rule run -> exactly one notification
    }

    @Test
    void failingHandlerIsRetriedWithBackoffThenMarkedFailed() {
        // an unknown action type makes ActionRegistry.get() throw inside the worker
        jdbc.update("""
                INSERT INTO applications (id, company, role, stage, last_activity_at, created_at, updated_at)
                VALUES ('00000000-0000-0000-0000-000000000001','X','Y','APPLIED', now(), now(), now())""");
        jdbc.update("""
                INSERT INTO rule_executions (id, idempotency_key, rule_id, application_id, action_type, status,
                    attempts, max_attempts, next_run_at, created_at, updated_at)
                VALUES (gen_random_uuid(), 'k1', gen_random_uuid(), '00000000-0000-0000-0000-000000000001',
                    'does_not_exist', 'PENDING', 0, 2, now(), now(), now())""");

        worker.runOnce();   // attempt 1 fails -> PENDING with backoff
        assertEquals(1, executions.countByStatus(RuleExecution.Status.PENDING));

        jdbc.update("UPDATE rule_executions SET next_run_at = now()");   // skip the wait
        worker.runOnce();   // attempt 2 fails -> FAILED (max_attempts = 2)
        assertEquals(1, executions.countByStatus(RuleExecution.Status.FAILED));
    }
}
