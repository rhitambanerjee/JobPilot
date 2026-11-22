package com.rhitam.jobtracker.rules.model;

import jakarta.validation.constraints.NotBlank;

public record Trigger(@NotBlank String eventType) {
}
