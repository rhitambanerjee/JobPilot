package com.rhitam.jobtracker.actions;

import com.rhitam.jobtracker.application.Application;
import com.rhitam.jobtracker.application.ApplicationService;
import com.rhitam.jobtracker.common.exception.BadRequestException;
import com.rhitam.jobtracker.emails.EmailTemplate;
import com.rhitam.jobtracker.emails.EmailTemplateRepository;
import com.rhitam.jobtracker.emails.OutgoingEmail;
import com.rhitam.jobtracker.emails.OutgoingEmailRepository;
import com.rhitam.jobtracker.emails.TemplateRenderer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Renders a saved template for one application and QUEUES the email (it does not send it: no SMTP calls inside
 * the database transaction). params: templateId (uuid string), placeholders (map of the user-supplied values).
 * The recipient is the contact email saved on the application.
 */
@Component
public class SendEmailHandler implements ActionHandler {

    public static final String TYPE = "send_email";

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH);

    private final EmailTemplateRepository templates;
    private final OutgoingEmailRepository outgoing;
    private final ApplicationService applications;
    private final Clock clock;

    public SendEmailHandler(EmailTemplateRepository templates, OutgoingEmailRepository outgoing,
                            ApplicationService applications, Clock clock) {
        this.templates = templates;
        this.outgoing = outgoing;
        this.applications = applications;
        this.clock = clock;
    }

    @Override
    public String type() {
        return TYPE;
    }

    /** Runs when a rule is saved: the template must exist and every user placeholder must have a value. */
    @Override
    public void validate(UUID userId, Map<String, Object> params) {
        EmailTemplate template = templates.findVisible(userId, templateId(params))
                .orElseThrow(() -> new BadRequestException("send_email: template not found"));
        Map<String, String> custom = customValues(params);
        List<String> missing = TemplateRenderer.customPlaceholders(template.getSubject(), template.getBody()).stream()
                .filter(name -> !custom.containsKey(name))
                .toList();
        if (!missing.isEmpty()) {
            throw new BadRequestException("send_email: fill in a value for " + missing);
        }
    }

    @Override
    public void execute(ActionContext context) {
        Application application = applications.get(context.userId(), context.applicationId());

        String to = application.getContactEmail();
        if (to == null || to.isBlank()) {
            // revivable: once the address is saved, the next matching event queues the email
            throw new ActionSkippedException("No contact email is saved for " + application.getCompany()
                    + ". Add the HR or careers address on the application and the email will be queued on the next daily check.");
        }

        EmailTemplate template = templates.findVisible(context.userId(), templateId(context.params()))
                .orElseThrow(() -> new NonRetryableActionException("The email template for this rule no longer exists"));

        Map<String, String> values = new HashMap<>(customValues(context.params()));
        values.putAll(builtInValues(application));

        List<String> missing = TemplateRenderer.missing(values, template.getSubject(), template.getBody());
        if (!missing.isEmpty()) {
            throw new NonRetryableActionException("Template \"" + template.getName() + "\" needs a value for "
                    + missing + ". Create a new rule with those filled in.");
        }

        String subject = TemplateRenderer.render(template.getSubject(), values).replaceAll("[\\r\\n]+", " ").trim();
        String body = TemplateRenderer.render(template.getBody(), values);
        outgoing.save(new OutgoingEmail(context.userId(), application.getId(), context.ruleId(), to.trim(), subject, body, clock.instant()));
    }

    private Map<String, String> builtInValues(Application application) {
        Instant now = clock.instant();
        String stage = application.getStage().name();
        Map<String, String> values = new HashMap<>();
        values.put("company", application.getCompany());
        values.put("role", application.getRole());
        values.put("stage", stage.charAt(0) + stage.substring(1).toLowerCase(Locale.ENGLISH));
        values.put("daysIdle", String.valueOf(Duration.between(application.getLastActivityAt(), now).toDays()));
        values.put("appliedDate", DATE.format(application.getCreatedAt().atZone(ZoneId.systemDefault())));
        values.put("jobUrl", application.getJobUrl() == null ? "" : application.getJobUrl());
        return values;
    }

    private static UUID templateId(Map<String, Object> params) {
        if (!(params.get("templateId") instanceof String id)) {
            throw new BadRequestException("send_email needs a templateId");
        }
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("send_email: templateId is not valid");
        }
    }

    /** Non-blank user-supplied values only; a blank value counts as "not filled in". */
    private static Map<String, String> customValues(Map<String, Object> params) {
        Map<String, String> values = new HashMap<>();
        if (params.get("placeholders") instanceof Map<?, ?> raw) {
            raw.forEach((key, value) -> {
                if (key instanceof String name && value != null && !String.valueOf(value).isBlank()) {
                    values.put(name, String.valueOf(value).trim());
                }
            });
        }
        return values;
    }
}
