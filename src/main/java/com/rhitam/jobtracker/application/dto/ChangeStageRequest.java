package com.rhitam.jobtracker.application.dto;

import com.rhitam.jobtracker.application.Stage;
import jakarta.validation.constraints.NotNull;

public record ChangeStageRequest(@NotNull Stage stage) {
}
