package com.rhitam.jobtracker.actions;

import java.util.Map;
import java.util.UUID;

/**
 * Strategy interface: one implementation per action type. Adding a new action means adding a class;
 * nothing in the engine or worker changes (open/closed principle).
 */
public interface ActionHandler {

    /** The identifier rules use, e.g. "create_reminder". */
    String type();

    /** Called when a rule is saved. Throw BadRequestException for bad params. */
    default void validate(UUID userId, Map<String, Object> params) {
    }

    /** Runs inside the worker's transaction together with the "mark succeeded" update. */
    void execute(ActionContext context);
}
