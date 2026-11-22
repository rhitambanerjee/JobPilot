package com.rhitam.jobtracker.actions;

import com.rhitam.jobtracker.application.Application;
import com.rhitam.jobtracker.application.ApplicationService;
import com.rhitam.jobtracker.application.Stage;
import org.springframework.stereotype.Component;

/** Goes through ApplicationService so archiving is audited and emits an event like any other change. */
@Component
public class ArchiveApplicationHandler implements ActionHandler {

    public static final String TYPE = "archive_application";
    public static final String ACTOR = "rule-engine";

    private final ApplicationService applications;

    public ArchiveApplicationHandler(ApplicationService applications) {
        this.applications = applications;
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public void execute(ActionContext context) {
        Application application = applications.get(context.applicationId());
        if (application.getStage() == Stage.ARCHIVED) {
            return;   // already done: handlers should be safe to run twice
        }
        applications.changeStage(context.applicationId(), Stage.ARCHIVED, ACTOR);
    }
}
