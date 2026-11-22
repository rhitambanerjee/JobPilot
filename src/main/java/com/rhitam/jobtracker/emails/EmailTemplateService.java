package com.rhitam.jobtracker.emails;

import com.rhitam.jobtracker.common.exception.BadRequestException;
import com.rhitam.jobtracker.common.exception.NotFoundException;
import com.rhitam.jobtracker.rules.Rule;
import com.rhitam.jobtracker.rules.RuleRepository;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmailTemplateService {

    private final EmailTemplateRepository templates;
    private final RuleRepository rules;
    private final Clock clock;

    public EmailTemplateService(EmailTemplateRepository templates, RuleRepository rules, Clock clock) {
        this.templates = templates;
        this.rules = rules;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<EmailTemplate> list(UUID userId) {
        return templates.findVisible(userId);
    }

    @Transactional
    public EmailTemplate create(UUID userId, String name, String subject, String body) {
        check(subject, body);
        return templates.save(new EmailTemplate(userId, name.trim(), subject.trim(), body, clock.instant()));
    }

    @Transactional
    public EmailTemplate update(UUID userId, UUID id, String name, String subject, String body) {
        EmailTemplate template = find(userId, id);
        if (template.isBuiltIn()) {
            throw new BadRequestException("Built-in templates can't be edited. Save a copy and edit that instead.");
        }
        check(subject, body);
        template.update(name.trim(), subject.trim(), body, clock.instant());
        return template;
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        EmailTemplate template = find(userId, id);
        if (template.isBuiltIn()) {
            throw new BadRequestException("Built-in templates can't be deleted.");
        }
        List<String> users = rules.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .filter(rule -> usesTemplate(rule, id))
                .map(Rule::getName)
                .toList();
        if (!users.isEmpty()) {
            throw new BadRequestException("This template is used by rule(s): " + String.join(", ", users)
                    + ". Delete those rules first.");
        }
        templates.delete(template);
    }

    /** Another user's template is "not found", exactly like one that doesn't exist. */
    private EmailTemplate find(UUID userId, UUID id) {
        return templates.findVisible(userId, id)
                .orElseThrow(() -> new NotFoundException("Template " + id + " not found"));
    }

    private static void check(String subject, String body) {
        if (TemplateRenderer.isMalformed(subject) || TemplateRenderer.isMalformed(body)) {
            throw new BadRequestException(
                    "Malformed placeholder. Use {{name}} with letters, digits or underscores, starting with a letter.");
        }
    }

    private static boolean usesTemplate(Rule rule, UUID templateId) {
        return rule.getActions().stream().anyMatch(action ->
                "send_email".equals(action.type())
                        && templateId.toString().equals(String.valueOf(action.params().get("templateId"))));
    }
}
