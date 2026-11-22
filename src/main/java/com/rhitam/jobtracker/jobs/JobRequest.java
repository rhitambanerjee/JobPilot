package com.rhitam.jobtracker.jobs;

import java.util.Map;
import java.util.UUID;

public record JobRequest(
        String idempotencyKey,
        UUID ruleId,
        UUID applicationId,
        String actionType,
        Map<String, Object> params) {
}
