package com.rhitam.jobtracker.outbox;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.rhitam.jobtracker.PostgresIT;
import com.rhitam.jobtracker.application.Application;
import com.rhitam.jobtracker.application.ApplicationService;
import com.rhitam.jobtracker.application.Stage;
import com.rhitam.jobtracker.application.dto.CreateApplicationRequest;
import com.rhitam.jobtracker.audit.AuditRepository;
import com.rhitam.jobtracker.jobs.RuleExecutionRepository;
import com.rhitam.jobtracker.rules.Rule;
import com.rhitam.jobtracker.rules.RuleRepository;
import com.rhitam.jobtracker.rules.model.ActionSpec;
import com.rhitam.jobtracker.rules.model.Condition;
import com.rhitam.jobtracker.rules.model.Operator;
import com.rhitam.jobtracker.rules.model.Trigger;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class OutboxPollerIT extends PostgresIT {

    @Autowired ApplicationService applications;
    @Autowired OutboxPoller poller;
    @Autowired OutboxRepository outbox;
    @Autowired RuleRepository rules;
    @Autowired RuleExecutionRepository executions;
    @Autowired AuditRepository audit;

    @Test
    void stageChangeWritesAuditAndEvent_andPollerTurnsMatchingRuleIntoAJob() {
        rules.save(new Rule("interview prep", new Trigger(EventTypes.APPLICATION_STAGE_CHANGED),
                List.of(new Condition("toStage", Operator.EQ, "INTERVIEW")),
                List.of(new ActionSpec("create_checklist", Map.of("title", "Interview prep"))),
                true, Instant.now()));

        Application app = applications.create(new CreateApplicationRequest("Rippling", "Backend Engineer", null, null, null, null));
        applications.changeStage(app.getId(), Stage.INTERVIEW, "test");

        // one transaction produced: audit rows (created + stage change) and outbox events (same two)
        assertEquals(2, audit.findByEntityIdOrderByCreatedAtDesc(app.getId()).size());
        assertEquals(2, outbox.countByStatus(OutboxEvent.Status.PENDING));

        assertEquals(2, poller.poll());

        assertEquals(0, outbox.countByStatus(OutboxEvent.Status.PENDING));
        assertEquals(2, outbox.countByStatus(OutboxEvent.Status.PROCESSED));
        assertEquals(1, executions.countByApplicationId(app.getId()));   // only the INTERVIEW event matched
    }

    @Test
    void illegalTransitionRollsBackEverything() {
        Application app = applications.create(new CreateApplicationRequest("Rippling", "Backend Engineer", null, null, null, null));
        long outboxBefore = outbox.count();
        long auditBefore = audit.count();

        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                () -> applications.changeStage(app.getId(), Stage.OFFER, "test"));   // APPLIED -> OFFER not allowed

        assertEquals(outboxBefore, outbox.count());
        assertEquals(auditBefore, audit.count());
    }
}
