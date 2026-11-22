package com.rhitam.jobtracker.actions;

import java.util.Map;
import java.util.UUID;

public record ActionContext(UUID applicationId, UUID ruleId, Map<String, Object> params) {
}
