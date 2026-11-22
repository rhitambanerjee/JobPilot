package com.rhitam.jobtracker.application;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** Pipeline stage plus the state machine of allowed moves. */
public enum Stage {
    WISHLIST, APPLIED, SCREENING, INTERVIEW, OFFER, ACCEPTED, REJECTED, WITHDRAWN, ARCHIVED;

    private static final Map<Stage, Set<Stage>> ALLOWED = new EnumMap<>(Stage.class);

    static {
        ALLOWED.put(WISHLIST, EnumSet.of(APPLIED, WITHDRAWN, ARCHIVED));
        ALLOWED.put(APPLIED, EnumSet.of(SCREENING, INTERVIEW, REJECTED, WITHDRAWN, ARCHIVED));
        ALLOWED.put(SCREENING, EnumSet.of(INTERVIEW, REJECTED, WITHDRAWN, ARCHIVED));
        ALLOWED.put(INTERVIEW, EnumSet.of(OFFER, REJECTED, WITHDRAWN, ARCHIVED));
        ALLOWED.put(OFFER, EnumSet.of(ACCEPTED, REJECTED, WITHDRAWN, ARCHIVED));
        ALLOWED.put(ACCEPTED, EnumSet.of(ARCHIVED));
        ALLOWED.put(REJECTED, EnumSet.of(ARCHIVED));
        ALLOWED.put(WITHDRAWN, EnumSet.of(ARCHIVED));
        ALLOWED.put(ARCHIVED, EnumSet.noneOf(Stage.class));
    }

    public boolean canTransitionTo(Stage next) {
        return ALLOWED.get(this).contains(next);
    }

    public Set<Stage> allowedNext() {
        return EnumSet.copyOf(ALLOWED.get(this));
    }
}
