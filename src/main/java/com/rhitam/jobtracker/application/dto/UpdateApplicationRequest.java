package com.rhitam.jobtracker.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The descriptive fields. Stage has its own endpoint (it is a state machine); contact email has its own too. */
public record UpdateApplicationRequest(
        @NotBlank @Size(max = 200) String company,
        @NotBlank @Size(max = 200) String role,
        @Size(max = 1000) String jobUrl,
        @Size(max = 5000) String notes) {
}
