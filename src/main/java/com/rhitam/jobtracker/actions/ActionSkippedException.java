package com.rhitam.jobtracker.actions;

/**
 * "Not possible right now, but may be later" (e.g. the application has no contact email yet).
 * The job is parked as SKIPPED and is revived the next time the same rule matches the same situation.
 */
public class ActionSkippedException extends RuntimeException {
    public ActionSkippedException(String message) {
        super(message);
    }
}
