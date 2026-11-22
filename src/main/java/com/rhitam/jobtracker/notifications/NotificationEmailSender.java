package com.rhitam.jobtracker.notifications;

import java.time.Clock;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Delivers PENDING notification emails. Same shape as the outbox poller: lock a small batch (SKIP LOCKED),
 * try each one, record the outcome. Delivery is at-least-once: if the process dies after SMTP accepted a
 * message but before the commit, that email is sent again on restart.
 */
@Component
public class NotificationEmailSender {

    private static final Logger log = LoggerFactory.getLogger(NotificationEmailSender.class);

    private final NotificationRepository repository;
    private final ObjectProvider<JavaMailSender> mailSender;   // absent when no SMTP host is configured
    private final TransactionTemplate tx;
    private final Clock clock;
    private final String from;
    private final int batchSize;
    private final int maxAttempts;

    public NotificationEmailSender(NotificationRepository repository, ObjectProvider<JavaMailSender> mailSender,
                                   TransactionTemplate tx, Clock clock,
                                   @Value("${tracker.mail.from:${spring.mail.username:job-tracker@localhost}}") String from,
                                   @Value("${tracker.notifications.email-batch-size:10}") int batchSize,
                                   @Value("${tracker.notifications.email-max-attempts:3}") int maxAttempts) {
        this.repository = repository;
        this.mailSender = mailSender;
        this.tx = tx;
        this.clock = clock;
        this.from = from;
        this.batchSize = batchSize;
        this.maxAttempts = maxAttempts;
    }

    @Scheduled(fixedDelayString = "${tracker.notifications.email-poll-ms:5000}")
    public void scheduledSend() {
        sendPending();
    }

    /** @return number of notifications handled in this batch */
    public int sendPending() {
        JavaMailSender sender = mailSender.getIfAvailable();
        Integer handled = tx.execute(status -> {
            List<Notification> batch = repository.lockPendingEmails(batchSize);
            for (Notification n : batch) {
                if (sender == null) {
                    n.failEmail("Mail server is not configured (set SPRING_MAIL_HOST and related settings)");
                    continue;
                }
                try {
                    SimpleMailMessage mail = new SimpleMailMessage();
                    mail.setFrom(from);
                    mail.setTo(n.getEmailTo());
                    mail.setSubject("[Job Tracker] " + n.getTitle());
                    mail.setText(n.getMessage() + "\n\nOpen Job Tracker to see the details.");
                    sender.send(mail);
                    n.markEmailSent(clock.instant());
                } catch (MailException e) {
                    log.warn("Email for notification {} failed: {}", n.getId(), e.getMessage());
                    n.recordEmailFailure(e.getMessage(), maxAttempts);
                }
            }
            return batch.size();
        });
        return handled == null ? 0 : handled;
    }
}
