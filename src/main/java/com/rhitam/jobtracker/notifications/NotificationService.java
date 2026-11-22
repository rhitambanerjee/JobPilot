package com.rhitam.jobtracker.notifications;

import com.rhitam.jobtracker.application.ApplicationRepository;
import com.rhitam.jobtracker.common.exception.BadRequestException;
import com.rhitam.jobtracker.common.exception.NotFoundException;
import com.rhitam.jobtracker.rules.Rule;
import com.rhitam.jobtracker.rules.RuleRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates notifications. recordRuleRun() is called by JobWorker INSIDE the job's transaction, so the
 * notification commits together with the action's own side effect and the "succeeded" flag: you can
 * never get a notification for work that rolled back, or work without its notification.
 * Emails are NOT sent here (an SMTP call inside a database transaction is a bad idea);
 * they are queued as PENDING and sent by NotificationEmailSender.
 */
@Service
public class NotificationService {

    private final NotificationRepository notifications;
    private final NotificationSettingsRepository settings;
    private final RuleRepository rules;
    private final ApplicationRepository applications;
    private final Clock clock;

    public NotificationService(NotificationRepository notifications, NotificationSettingsRepository settings,
                               RuleRepository rules, ApplicationRepository applications, Clock clock) {
        this.notifications = notifications;
        this.settings = settings;
        this.rules = rules;
        this.applications = applications;
        this.clock = clock;
    }

    @Transactional
    public Notification recordRuleRun(UUID ruleId, UUID applicationId, String actionType) {
        String ruleName = rules.findById(ruleId).map(Rule::getName).orElse("(deleted rule)");
        String target = applications.findById(applicationId)
                .map(a -> a.getCompany() + " \u2014 " + a.getRole())
                .orElse("an application");

        String title = "Rule ran: " + ruleName;
        String message = "Rule \"" + ruleName + "\" " + describe(actionType) + " for " + target + ".";
        return notifications.save(new Notification(applicationId, ruleId, title, message, emailTarget(), clock.instant()));
    }

    /** In-app only (never emailed): a rule could not do its job, with the reason. */
    @Transactional
    public Notification recordProblem(UUID ruleId, UUID applicationId, String reason) {
        String ruleName = rules.findById(ruleId).map(Rule::getName).orElse("(deleted rule)");
        String message = "Rule \"" + ruleName + "\" could not run for " + describeApplication(applicationId) + ": " + reason;
        return notifications.save(new Notification(applicationId, ruleId, "Rule problem: " + ruleName,
                limit(message), null, clock.instant()));
    }

    /** In-app only: the outcome of an email the app sent (or failed to send) to HR on the user's behalf. */
    @Transactional
    public Notification recordEmailOutcome(UUID applicationId, UUID ruleId, String to, boolean sent, String error) {
        String target = describeApplication(applicationId);
        String title = sent ? "Email sent" : "Email failed";
        String message = sent
                ? "Your email to " + to + " about " + target + " was sent."
                : "Your email to " + to + " about " + target + " could not be sent: " + error;
        return notifications.save(new Notification(applicationId, ruleId, title, limit(message), null, clock.instant()));
    }

    private String describeApplication(UUID applicationId) {
        return applications.findById(applicationId)
                .map(a -> a.getCompany() + " \u2014 " + a.getRole())
                .orElse("an application");
    }

    private static String limit(String text) {
        return text.length() <= 990 ? text : text.substring(0, 990) + "...";
    }

    @Transactional(readOnly = true)
    public List<Notification> latest() {
        return notifications.findTop100ByOrderByCreatedAtDesc();
    }

    @Transactional
    public Notification markRead(UUID id) {
        Notification n = notifications.findById(id)
                .orElseThrow(() -> new NotFoundException("Notification " + id + " not found"));
        n.markRead(clock.instant());
        return n;
    }

    @Transactional
    public void markAllRead() {
        notifications.markAllRead(clock.instant());
    }

    @Transactional(readOnly = true)
    public NotificationSettings getSettings() {
        return settings.findById(NotificationSettings.SINGLETON_ID)
                .orElseGet(() -> new NotificationSettings(null, false, clock.instant()));
    }

    @Transactional
    public NotificationSettings saveSettings(String email, boolean emailEnabled) {
        String cleaned = email == null || email.isBlank() ? null : email.trim();
        if (emailEnabled && cleaned == null) {
            throw new BadRequestException("Enter an email address to turn on email notifications");
        }
        Instant now = clock.instant();
        NotificationSettings current = settings.findById(NotificationSettings.SINGLETON_ID).orElse(null);
        if (current == null) {
            return settings.save(new NotificationSettings(cleaned, emailEnabled, now));
        }
        current.update(cleaned, emailEnabled, now);
        return current;
    }

    /** Lets the user verify the whole email path (settings, SMTP, delivery) with one click. */
    @Transactional
    public Notification sendTest() {
        NotificationSettings current = getSettings();
        if (!current.canEmail()) {
            throw new BadRequestException("Turn on email notifications and save your address first");
        }
        return notifications.save(new Notification(null, null, "Test notification",
                "This is a test. If you can read this email, notifications are working.",
                current.getEmail(), clock.instant()));
    }

    private String emailTarget() {
        NotificationSettings current = settings.findById(NotificationSettings.SINGLETON_ID).orElse(null);
        return current != null && current.canEmail() ? current.getEmail() : null;
    }

    private static String describe(String actionType) {
        return switch (actionType) {
            case "create_checklist" -> "created a checklist";
            case "create_reminder" -> "created a reminder";
            case "archive_application" -> "archived the application";
            case "send_email" -> "queued a follow-up email";
            default -> "ran action " + actionType;
        };
    }
}
