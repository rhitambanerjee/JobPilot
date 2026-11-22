package com.rhitam.jobtracker.actions;

/** The action can never succeed as configured (retrying is pointless). The job fails immediately. */
public class NonRetryableActionException extends RuntimeException {
    public NonRetryableActionException(String message) {
        super(message);
    }
}
