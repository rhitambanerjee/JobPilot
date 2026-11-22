package com.rhitam.jobtracker.tenancy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rhitam.jobtracker.PostgresIT;
import com.rhitam.jobtracker.application.Application;
import com.rhitam.jobtracker.application.ApplicationService;
import com.rhitam.jobtracker.application.Stage;
import com.rhitam.jobtracker.application.dto.CreateApplicationRequest;
import com.rhitam.jobtracker.common.exception.BadRequestException;
import com.rhitam.jobtracker.common.exception.NotFoundException;
import com.rhitam.jobtracker.emails.EmailTemplate;
import com.rhitam.jobtracker.emails.EmailTemplateService;
import com.rhitam.jobtracker.jobs.JobWorker;
import com.rhitam.jobtracker.jobs.RuleExecutionRepository;
import com.rhitam.jobtracker.notifications.NotificationRepository;
import com.rhitam.jobtracker.notifications.NotificationService;
import com.rhitam.jobtracker.outbox.EventTypes;
import com.rhitam.jobtracker.outbox.OutboxPoller;
import com.rhitam.jobtracker.rules.Rule;
import com.rhitam.jobtracker.rules.RuleRepository;
import com.rhitam.jobtracker.rules.RuleValidator;
import com.rhitam.jobtracker.rules.model.ActionSpec;
import com.rhitam.jobtracker.rules.model.RuleRequest;
import com.rhitam.jobtracker.rules.model.Trigger;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mail.javamail.JavaMailSender;

/** One user must never see, change, or trigger anything that belongs to another user. */
class TenantIsolationIT extends PostgresIT {

    @MockBean JavaMailSender mailSender;

    @Autowired ApplicationService applications;
    @Autowired RuleRepository rules;
    @Autowired RuleValidator ruleValidator;
    @Autowired EmailTemplateService templateService;
    @Autowired NotificationService notificationService;
    @Autowired NotificationRepository notifications;
    @Autowired RuleExecutionRepository executions;
    @Autowired OutboxPoller poller;
    @Autowired JobWorker worker;

    private static CreateApplicationRequest request(String company) {
        return new CreateApplicationRequest(company, "Engineer", null, null, null, null);
    }

    @Test
    void usersOnlySeeAndChangeTheirOwnApplications() {
        UUID alice = newUser();
        UUID bob = newUser();
        Application alices = applications.create(alice, request("Acme"));

        assertEquals(1, applications.list(alice, null).size());
        assertEquals(0, applications.list(bob, null).size());
        assertThrows(NotFoundException.class, () -> applications.get(bob, alices.getId()));
        assertThrows(NotFoundException.class,
                () -> applications.changeStage(bob, alices.getId(), Stage.SCREENING, "bob"));
        assertThrows(NotFoundException.class,
                () -> applications.updateContactEmail(bob, alices.getId(), "hr@evil.example"));
    }

    @Test
    void aRuleOnlyFiresForItsOwnersApplications() {
        UUID alice = newUser();
        UUID bob = newUser();
        rules.save(new Rule(alice, "remind me", new Trigger(EventTypes.APPLICATION_CREATED), List.of(),
                List.of(new ActionSpec("create_reminder", Map.of())), true, Instant.now()));

        Application bobs = applications.create(bob, request("BobCo"));
        poller.poll();
        worker.runOnce();
        assertEquals(0, executions.countByApplicationId(bobs.getId()));          // Alice's rule ignored Bob's event
        assertEquals(0, notifications.findTop100ByUserIdOrderByCreatedAtDesc(alice).size());

        Application alices = applications.create(alice, request("AliceCo"));
        poller.poll();
        worker.runOnce();
        assertEquals(1, executions.countByApplicationId(alices.getId()));
        assertEquals(1, notifications.findTop100ByUserIdOrderByCreatedAtDesc(alice).size());
        assertEquals(0, notifications.findTop100ByUserIdOrderByCreatedAtDesc(bob).size());
    }

    @Test
    void savedTemplatesArePrivate_builtInsAreShared() {
        UUID alice = newUser();
        UUID bob = newUser();
        EmailTemplate mine = templateService.create(alice, "mine", "Hi {{company}}", "Thanks, {{yourName}}");

        assertTrue(templateService.list(alice).stream().anyMatch(t -> t.getId().equals(mine.getId())));
        assertFalse(templateService.list(bob).stream().anyMatch(t -> t.getId().equals(mine.getId())));
        assertTrue(templateService.list(bob).stream().anyMatch(EmailTemplate::isBuiltIn));

        assertThrows(NotFoundException.class, () -> templateService.update(bob, mine.getId(), "x", "y", "z"));
        assertThrows(NotFoundException.class, () -> templateService.delete(bob, mine.getId()));

        // Bob cannot build a rule around Alice's template either
        RuleRequest bobsRule = new RuleRequest("sneaky", new Trigger(EventTypes.APPLICATION_STALE), List.of(),
                List.of(new ActionSpec("send_email",
                        Map.of("templateId", mine.getId().toString(), "placeholders", Map.of("yourName", "Bob")))),
                true);
        assertThrows(BadRequestException.class, () -> ruleValidator.validate(bob, bobsRule));
    }

    @Test
    void notificationsAndSettingsArePerUser() {
        UUID alice = newUser();
        UUID bob = newUser();
        notificationService.saveSettings(alice, "alice@example.com", true);

        assertEquals("alice@example.com", notificationService.getSettings(alice).getEmail());
        assertFalse(notificationService.getSettings(bob).isEmailEnabled());
        assertEquals(null, notificationService.getSettings(bob).getEmail());

        UUID aliceNotification = notificationService.sendTest(alice).getId();
        assertEquals(0, notificationService.latest(bob).size());
        assertThrows(NotFoundException.class, () -> notificationService.markRead(bob, aliceNotification));
    }
}
