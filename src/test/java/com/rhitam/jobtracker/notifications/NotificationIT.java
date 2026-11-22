package com.rhitam.jobtracker.notifications;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.rhitam.jobtracker.PostgresIT;
import com.rhitam.jobtracker.application.ApplicationService;
import com.rhitam.jobtracker.application.dto.CreateApplicationRequest;
import com.rhitam.jobtracker.jobs.JobWorker;
import com.rhitam.jobtracker.outbox.EventTypes;
import com.rhitam.jobtracker.outbox.OutboxPoller;
import com.rhitam.jobtracker.rules.Rule;
import com.rhitam.jobtracker.rules.RuleRepository;
import com.rhitam.jobtracker.rules.model.ActionSpec;
import com.rhitam.jobtracker.rules.model.Trigger;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

class NotificationIT extends PostgresIT {

    @MockBean JavaMailSender mailSender;

    @Autowired ApplicationService applications;
    @Autowired RuleRepository rules;
    @Autowired OutboxPoller poller;
    @Autowired JobWorker worker;
    @Autowired NotificationRepository notifications;
    @Autowired NotificationSettingsRepository settings;
    @Autowired NotificationEmailSender emailSender;

    /** One rule that reacts to a new application, one application, then run the whole pipeline once. */
    private void runRulePipeline() {
        rules.save(new Rule("follow up", new Trigger(EventTypes.APPLICATION_CREATED), List.of(),
                List.of(new ActionSpec("create_reminder", Map.of("message", "Follow up"))), true, Instant.now()));
        applications.create(new CreateApplicationRequest("Rippling", "Backend Engineer", null, null, null, null));
        poller.poll();
        worker.runOnce();
    }

    @Test
    void ruleRunCreatesInAppNotification_andNoEmailWithoutOptIn() {
        runRulePipeline();

        List<Notification> all = notifications.findAll();
        assertEquals(1, all.size());
        assertEquals(Notification.EmailStatus.NONE, all.get(0).getEmailStatus());
        assertNull(all.get(0).getEmailTo());
        assertEquals(0, emailSender.sendPending());
        verifyNoInteractions(mailSender);
    }

    @Test
    void optedInUserGetsTheNotificationByEmail() {
        settings.save(new NotificationSettings("me@example.com", true, Instant.now()));
        runRulePipeline();

        Notification n = notifications.findAll().get(0);
        assertEquals(Notification.EmailStatus.PENDING, n.getEmailStatus());
        assertEquals("me@example.com", n.getEmailTo());

        assertEquals(1, emailSender.sendPending());

        verify(mailSender).send(any(SimpleMailMessage.class));
        assertEquals(Notification.EmailStatus.SENT, notifications.findById(n.getId()).orElseThrow().getEmailStatus());
    }

    @Test
    void emailIsRetriedThenMarkedFailed_butTheInAppNotificationSurvives() {
        settings.save(new NotificationSettings("me@example.com", true, Instant.now()));
        doThrow(new MailSendException("smtp down")).when(mailSender).send(any(SimpleMailMessage.class));
        runRulePipeline();
        Notification n = notifications.findAll().get(0);

        emailSender.sendPending();   // attempt 1 -> still PENDING
        assertEquals(Notification.EmailStatus.PENDING, notifications.findById(n.getId()).orElseThrow().getEmailStatus());
        emailSender.sendPending();   // attempt 2
        emailSender.sendPending();   // attempt 3 (max) -> FAILED

        Notification after = notifications.findById(n.getId()).orElseThrow();
        assertEquals(Notification.EmailStatus.FAILED, after.getEmailStatus());
        assertEquals(1, notifications.count());
    }
}
