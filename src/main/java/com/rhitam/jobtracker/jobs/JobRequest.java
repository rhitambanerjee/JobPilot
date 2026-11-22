package com.rhitam.jobtracker.jobs;

import java.util.Map;
import java.util.UUID;

public record JobRequest(
        UUID userId,
        String idempotencyKey,
        UUID ruleId,
        UUID applicationId,
        String actionType,
        Map<String, Object> params) {
}
