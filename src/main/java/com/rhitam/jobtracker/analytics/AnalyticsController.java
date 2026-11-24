package com.rhitam.jobtracker.analytics;

import com.rhitam.jobtracker.auth.AuthenticatedUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final AnalyticsService service;

    public AnalyticsController(AnalyticsService service) {
        this.service = service;
    }

    /** GET /api/analytics/funnel?weeks=12 (weeks is clamped to 1..52). */
    @GetMapping("/funnel")
    public FunnelReport funnel(@AuthenticationPrincipal AuthenticatedUser user,
                               @RequestParam(defaultValue = "12") int weeks) {
        return service.funnel(user.id(), Math.max(1, Math.min(weeks, 52)));
    }
}
