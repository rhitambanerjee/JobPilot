package com.rhitam.jobtracker.rules.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** field is a dotted path into the event payload, e.g. "toStage" or "daysIdle". */
public record Condition(@NotBlank String field, @NotNull Operator op, Object value) {
}
