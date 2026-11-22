package com.rhitam.jobtracker.scheduler;

import com.rhitam.jobtracker.application.Application;
import com.rhitam.jobtracker.application.ApplicationRepository;
import com.rhitam.jobtracker.application.Stage;
import com.rhitam.jobtracker.outbox.EventTypes;
import com.rhitam.jobtracker.outbox.OutboxPublisher;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Time-based source of events. Every day it emits "application.stale" (with daysIdle) for each Applied or
 * Screening application idle for at least tracker.stale.days. It does not act itself; user rules decide, for
 * example "stage = APPLIED and daysIdle >= 7 -> send follow-up email".
 *
 * The event carries a dedupeKey (application + start of the idle stretch), so a rule fires at most ONCE per
 * idle stretch even though the event repeats daily. Any real activity (a stage change) starts a new stretch.
 */
@Component
public class StaleApplicationScheduler {

    private final ApplicationRepository applications;
    private final OutboxPublisher outbox;
    private final Clock clock;
    private final int minIdleDays;

    public StaleApplicationScheduler(ApplicationRepository applications, OutboxPublisher outbox, Clock clock,
                                     @Value("${tracker.stale.days:1}") int minIdleDays) {
        this.applications = applications;
        this.outbox = outbox;
        this.clock = clock;
        this.minIdleDays = minIdleDays;
    }

    /** @return number of stale events emitted */
    @Scheduled(cron = "${tracker.stale.cron:0 0 9 * * *}")
    @Transactional
    public int scan() {
        Instant now = clock.instant();
        Instant cutoff = now.minus(minIdleDays, ChronoUnit.DAYS);
        List<Application> idle = applications.findStale(EnumSet.of(Stage.APPLIED, Stage.SCREENING), cutoff);
        for (Application application : idle) {
            long daysIdle = Duration.between(application.getLastActivityAt(), now).toDays();
            outbox.publish(application.getUserId(), "APPLICATION", application.getId(), EventTypes.APPLICATION_STALE, Map.of(
                    "applicationId", application.getId().toString(),
                    "company", application.getCompany(),
                    "role", application.getRole(),
                    "stage", application.getStage().name(),
                    "daysIdle", daysIdle,
                    "dedupeKey", "stale:" + application.getId() + ":" + application.getLastActivityAt().toEpochMilli()));
        }
        return idle.size();
    }
}
