package com.rhitam.jobtracker.rules.model;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

public record ActionSpec(@NotBlank String type, Map<String, Object> params) {

    public ActionSpec {
        params = params == null ? Map.of() : params;
    }
}
