package com.rhitam.jobtracker.application.dto;

import com.rhitam.jobtracker.application.Application;
import com.rhitam.jobtracker.application.Stage;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record ApplicationResponse(
        UUID id,
        String company,
        String role,
        String jobUrl,
        String contactEmail,
        Stage stage,
        Set<Stage> allowedNext,
        String notes,
        Instant lastActivityAt,
        Instant createdAt,
        Instant updatedAt) {

    public static ApplicationResponse from(Application a) {
        return new ApplicationResponse(a.getId(), a.getCompany(), a.getRole(), a.getJobUrl(), a.getContactEmail(), a.getStage(),
                a.getStage().allowedNext(), a.getNotes(), a.getLastActivityAt(), a.getCreatedAt(), a.getUpdatedAt());
    }
}
