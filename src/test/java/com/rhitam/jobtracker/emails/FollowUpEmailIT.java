package com.rhitam.jobtracker.emails;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.rhitam.jobtracker.PostgresIT;
import com.rhitam.jobtracker.application.Application;
import com.rhitam.jobtracker.application.ApplicationService;
import com.rhitam.jobtracker.application.dto.CreateApplicationRequest;
import com.rhitam.jobtracker.common.exception.BadRequestException;
import com.rhitam.jobtracker.jobs.JobWorker;
import com.rhitam.jobtracker.jobs.RuleExecution;
import com.rhitam.jobtracker.jobs.RuleExecutionRepository;
import com.rhitam.jobtracker.notifications.NotificationRepository;
import com.rhitam.jobtracker.outbox.EventTypes;
import com.rhitam.jobtracker.outbox.OutboxPoller;
import com.rhitam.jobtracker.rules.Rule;
import com.rhitam.jobtracker.rules.RuleRepository;
import com.rhitam.jobtracker.rules.model.ActionSpec;
import com.rhitam.jobtracker.rules.model.Condition;
import com.rhitam.jobtracker.rules.model.Operator;
import com.rhitam.jobtracker.rules.model.Trigger;
import com.rhitam.jobtracker.scheduler.StaleApplicationScheduler;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/** The headline scenario: Applied for 7+ days -> a follow-up email is sent to the saved HR address, once. */
class FollowUpEmailIT extends PostgresIT {

    @MockBean JavaMailSender mailSender;

    @Autowired ApplicationService applications;
    @Autowired RuleRepository rules;
    @Autowired EmailTemplateRepository templates;
    @Autowired EmailTemplateService templateService;
    @Autowired StaleApplicationScheduler stale;
    @Autowired OutboxPoller poller;
    @Autowired JobWorker worker;
    @Autowired OutgoingEmailSender sender;
    @Autowired OutgoingEmailRepository outgoing;
    @Autowired RuleExecutionRepository executions;
    @Autowired NotificationRepository notifications;

    private EmailTemplate newTemplate() {
        return templates.save(new EmailTemplate("test template", "Hello {{company}}",
                "Hi {{recruiterName}}, re {{role}} after {{daysIdle}} days. {{yourName}}", Instant.now()));
    }

    private void sevenDayFollowUpRule(EmailTemplate template) {
        rules.save(new Rule("7-day follow up", new Trigger(EventTypes.APPLICATION_STALE),
                List.of(new Condition("stage", Operator.EQ, "APPLIED"), new Condition("daysIdle", Operator.GTE, 7)),
                List.of(new ActionSpec("send_email", Map.of(
                        "templateId", template.getId().toString(),
                        "placeholders", Map.of("recruiterName", "Priya", "yourName", "Rhitam")))),
                true, Instant.now()));
    }

    private Application idleApplication(String contactEmail) {
        Application app = applications.create(
                new CreateApplicationRequest("Acme", "Backend Engineer", null, null, null, contactEmail));
        jdbc.update("UPDATE applications SET last_activity_at = now() - interval '8 days 1 hour' WHERE id = ?", app.getId());
        return app;
    }

    /** One "day": the daily scan emits events, the poller feeds the rules, the worker runs the jobs. */
    private void runOneDay() {
        stale.scan();
        poller.poll();
        worker.runOnce();
    }

    @Test
    void idleApplicationGetsOneFollowUpEmail_andOnlyOnce() {
        sevenDayFollowUpRule(newTemplate());
        idleApplication("hr@acme.com");

        runOneDay();
        assertEquals(1, outgoing.count());
        assertEquals(1, sender.sendPending());

        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(sent.capture());
        assertEquals("hr@acme.com", sent.getValue().getTo()[0]);
        assertEquals("Hello Acme", sent.getValue().getSubject());
        assertTrue(sent.getValue().getText().contains("Hi Priya, re Backend Engineer after 8 days. Rhitam"));

        runOneDay();   // the next day emits the stale event again, same idle stretch
        sender.sendPending();
        assertEquals(1, outgoing.count());
        verify(mailSender, times(1)).send(any(SimpleMailMessage.class));
    }

    @Test
    void missingContactEmailSkipsOnce_thenSendsAfterAddressIsSaved() {
        sevenDayFollowUpRule(newTemplate());
        Application app = idleApplication(null);

        runOneDay();
        assertEquals(1, executions.countByStatus(RuleExecution.Status.SKIPPED));
        assertEquals(0, outgoing.count());
        assertEquals(1, notifications.count());          // one "rule problem" notification

        runOneDay();                                     // still no address: stays skipped, no second notification
        assertEquals(1, executions.countByStatus(RuleExecution.Status.SKIPPED));
        assertEquals(1, notifications.count());

        applications.updateContactEmail(app.getId(), "hr@acme.com");
        runOneDay();                                     // the skipped job is revived and now succeeds
        assertEquals(1, executions.countByStatus(RuleExecution.Status.SUCCEEDED));
        assertEquals(1, outgoing.count());
    }

    @Test
    void builtInAndInUseTemplatesCannotBeDeleted() {
        EmailTemplate builtIn = templates.findAllByOrderByBuiltInDescNameAsc().get(0);
        assertThrows(BadRequestException.class, () -> templateService.delete(builtIn.getId()));

        EmailTemplate mine = newTemplate();
        sevenDayFollowUpRule(mine);
        assertThrows(BadRequestException.class, () -> templateService.delete(mine.getId()));
    }
}
