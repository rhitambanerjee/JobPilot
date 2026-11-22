package com.rhitam.jobtracker.actions;

import java.util.Map;
import java.util.UUID;

/** Everything a handler needs. userId is the rule's owner: handlers must only touch that user's data. */
public record ActionContext(UUID userId, UUID applicationId, UUID ruleId, Map<String, Object> params) {
}
