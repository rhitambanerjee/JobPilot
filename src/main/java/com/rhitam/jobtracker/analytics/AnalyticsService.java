package com.rhitam.jobtracker.analytics;

import com.rhitam.jobtracker.application.Application;
import com.rhitam.jobtracker.application.ApplicationRepository;
import com.rhitam.jobtracker.audit.AuditLog;
import com.rhitam.jobtracker.audit.AuditRepository;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnalyticsService {

    private final ApplicationRepository applications;
    private final AuditRepository audit;
    private final Clock clock;

    public AnalyticsService(ApplicationRepository applications, AuditRepository audit, Clock clock) {
        this.applications = applications;
        this.audit = audit;
        this.clock = clock;
    }

    /** Reads only this user's applications and history, then lets the pure calculator do the maths. */
    @Transactional(readOnly = true)
    public FunnelReport funnel(UUID userId, int weeks) {
        List<Application> userApplications = applications.findByUserIdOrderByUpdatedAtDesc(userId);
        List<AuditLog> history = audit.findByUserIdAndActionInOrderByEntityIdAscCreatedAtAsc(
                userId, List.of("CREATED", "STAGE_CHANGED"));
        return FunnelCalculator.calculate(userApplications, history, clock.instant(), weeks);
    }
}
