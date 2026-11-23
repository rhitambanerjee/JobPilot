package com.rhitam.jobtracker.emails;

import com.rhitam.jobtracker.auth.UserRepository;
import com.rhitam.jobtracker.notifications.NotificationService;
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
 * Delivers queued follow-up emails. Every user's email goes out through the app's one SMTP account, so each
 * message gets that user's own account address as Reply-To: HR's reply lands in the user's inbox, not ours.
 * Same shape as the other pollers: lock a small batch (SKIP LOCKED), try each one, record the outcome.
 * At-least-once: a crash right after SMTP accepts a message can send that one email again on restart.
 */
@Component
public class OutgoingEmailSender {

    private static final Logger log = LoggerFactory.getLogger(OutgoingEmailSender.class);

    private final OutgoingEmailRepository repository;
    private final UserRepository users;
    private final ObjectProvider<JavaMailSender> mailSender;
    private final NotificationService notifications;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final String from;
    private final int batchSize;
    private final int maxAttempts;
    private final boolean logAccountEmails;

    public OutgoingEmailSender(OutgoingEmailRepository repository, UserRepository users,
                               ObjectProvider<JavaMailSender> mailSender, NotificationService notifications,
                               TransactionTemplate tx, Clock clock,
                               @Value("${tracker.mail.from:${spring.mail.username:job-tracker@localhost}}") String from,
                               @Value("${tracker.outgoing.batch-size:10}") int batchSize,
                               @Value("${tracker.outgoing.max-attempts:3}") int maxAttempts,
                               @Value("${tracker.auth.log-emails:false}") boolean logAccountEmails) {
        this.repository = repository;
        this.users = users;
        this.mailSender = mailSender;
        this.notifications = notifications;
        this.tx = tx;
        this.clock = clock;
        this.from = from;
        this.batchSize = batchSize;
        this.maxAttempts = maxAttempts;
        this.logAccountEmails = logAccountEmails;
    }

    @Scheduled(fixedDelayString = "${tracker.outgoing.poll-ms:5000}")
    public void scheduledSend() {
        sendPending();
    }

    /** @return number of emails handled in this batch */
    public int sendPending() {
        JavaMailSender sender = mailSender.getIfAvailable();
        Integer handled = tx.execute(status -> {
            List<OutgoingEmail> batch = repository.lockPending(batchSize);
            for (OutgoingEmail email : batch) {
                if (logAccountEmails && email.getKind() == OutgoingEmail.Kind.ACCOUNT) {
                    log.info("DEV account email to {}\nSubject: {}\n{}", email.getToAddress(), email.getSubject(), email.getBody());
                }
                if (sender == null) {
                    email.failPermanently("Mail server is not configured (set SPRING_MAIL_HOST and related settings)");
                    notifyOutcome(email, false);
                    continue;
                }
                try {
                    SimpleMailMessage mail = new SimpleMailMessage();
                    mail.setFrom(from);
                    if (email.getKind() == OutgoingEmail.Kind.FOLLOW_UP) {   // account mail is from us, not from the user
                        users.findById(email.getUserId()).ifPresent(owner -> mail.setReplyTo(owner.getEmail()));
                    }
                    mail.setTo(email.getToAddress());
                    mail.setSubject(email.getSubject());
                    mail.setText(email.getBody());
                    sender.send(mail);
                    email.markSent(clock.instant());
                    notifyOutcome(email, true);
                } catch (MailException e) {
                    log.warn("Outgoing email {} failed: {}", email.getId(), e.getMessage());
                    email.recordFailure(e.getMessage(), maxAttempts);
                    if (email.getStatus() == OutgoingEmail.Status.FAILED) {
                        notifyOutcome(email, false);   // only tell the user once retries are used up
                    }
                }
            }
            return batch.size();
        });
        return handled == null ? 0 : handled;
    }

    private void notifyOutcome(OutgoingEmail email, boolean sent) {
        if (email.getKind() == OutgoingEmail.Kind.ACCOUNT) {
            return;   // no application to attach an in-app notification to
        }
        notifications.recordEmailOutcome(email.getUserId(), email.getApplicationId(), email.getRuleId(),
                email.getToAddress(), sent, email.getLastError());
    }
}
