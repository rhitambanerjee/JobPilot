package com.rhitam.jobtracker.application;

import com.rhitam.jobtracker.application.dto.CreateApplicationRequest;
import com.rhitam.jobtracker.audit.AuditLog;
import com.rhitam.jobtracker.audit.AuditRepository;
import com.rhitam.jobtracker.common.exception.BadRequestException;
import com.rhitam.jobtracker.common.exception.NotFoundException;
import com.rhitam.jobtracker.outbox.EventTypes;
import com.rhitam.jobtracker.outbox.OutboxPublisher;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use-case layer. Every method takes the owner's userId explicitly: controllers pass the logged-in user,
 * background jobs pass the owner stored on the job. Loading by (id, userId) means someone else's application
 * is indistinguishable from one that doesn't exist.
 *
 * Every state change does three things in ONE transaction: update the aggregate, write an audit row,
 * write an outbox event. Either all three commit or none do.
 */
@Service
public class ApplicationService {

    public static final String ACTOR_USER = "user";

    private final ApplicationRepository applications;
    private final AuditRepository audit;
    private final OutboxPublisher outbox;
    private final Clock clock;

    public ApplicationService(ApplicationRepository applications, AuditRepository audit,
                              OutboxPublisher outbox, Clock clock) {
        this.applications = applications;
        this.audit = audit;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Transactional
    public Application create(UUID userId, CreateApplicationRequest req) {
        Stage initial = req.stage() == null ? Stage.APPLIED : req.stage();
        if (initial != Stage.APPLIED && initial != Stage.WISHLIST) {
            throw new BadRequestException("New applications must start as WISHLIST or APPLIED");
        }
        Instant now = clock.instant();
        Application toSave = new Application(userId, req.company(), req.role(), req.jobUrl(), req.notes(), initial, now);
        toSave.updateContactEmail(clean(req.contactEmail()), now);
        Application saved = applications.save(toSave);

        audit.save(AuditLog.of(userId, "APPLICATION", saved.getId(), "CREATED", ACTOR_USER,
                Map.of("company", saved.getCompany(), "role", saved.getRole(), "stage", initial.name()), now));
        outbox.publish(userId, "APPLICATION", saved.getId(), EventTypes.APPLICATION_CREATED, Map.of(
                "applicationId", saved.getId().toString(),
                "company", saved.getCompany(),
                "role", saved.getRole(),
                "stage", initial.name()));
        return saved;
    }

    @Transactional
    public Application changeStage(UUID userId, UUID id, Stage next, String actor) {
        Application application = get(userId, id);
        Instant now = clock.instant();
        Stage previous = application.moveTo(next, now);

        audit.save(AuditLog.of(userId, "APPLICATION", id, "STAGE_CHANGED", actor,
                Map.of("from", previous.name(), "to", next.name()), now));
        outbox.publish(userId, "APPLICATION", id, EventTypes.APPLICATION_STAGE_CHANGED, Map.of(
                "applicationId", id.toString(),
                "company", application.getCompany(),
                "role", application.getRole(),
                "fromStage", previous.name(),
                "toStage", next.name()));
        return application;
    }

    /** Saves (or clears, when blank) the HR / careers address that follow-up emails are sent to. */
    @Transactional
    public Application updateContactEmail(UUID userId, UUID id, String email) {
        Application application = get(userId, id);
        Instant now = clock.instant();
        String cleaned = clean(email);
        application.updateContactEmail(cleaned, now);
        audit.save(AuditLog.of(userId, "APPLICATION", id, "CONTACT_UPDATED", ACTOR_USER,
                Map.of("contactEmail", cleaned == null ? "" : cleaned), now));
        return application;
    }

    private static String clean(String email) {
        return email == null || email.isBlank() ? null : email.trim();
    }

    @Transactional(readOnly = true)
    public Application get(UUID userId, UUID id) {
        return applications.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Application " + id + " not found"));
    }

    @Transactional(readOnly = true)
    public List<Application> list(UUID userId, Stage stage) {
        return stage == null
                ? applications.findByUserIdOrderByUpdatedAtDesc(userId)
                : applications.findByUserIdAndStageOrderByUpdatedAtDesc(userId, stage);
    }
}
