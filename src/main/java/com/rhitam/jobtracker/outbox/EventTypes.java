package com.rhitam.jobtracker.outbox;

import java.util.Set;

public final class EventTypes {

    public static final String APPLICATION_CREATED = "application.created";
    public static final String APPLICATION_STAGE_CHANGED = "application.stage_changed";
    public static final String APPLICATION_STALE = "application.stale";

    public static final Set<String> ALL =
            Set.of(APPLICATION_CREATED, APPLICATION_STAGE_CHANGED, APPLICATION_STALE);

    private EventTypes() {
    }
}
