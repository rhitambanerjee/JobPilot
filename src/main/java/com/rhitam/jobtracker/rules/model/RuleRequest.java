package com.rhitam.jobtracker.rules.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record RuleRequest(
        @NotBlank String name,
        @NotNull @Valid Trigger trigger,
        List<@Valid Condition> conditions,
        @NotEmpty List<@Valid ActionSpec> actions,
        Boolean enabled) {

    public RuleRequest {
        conditions = conditions == null ? List.of() : conditions;
        enabled = enabled == null || enabled;
    }
}
