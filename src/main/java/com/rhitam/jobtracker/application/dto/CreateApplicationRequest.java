package com.rhitam.jobtracker.application.dto;

import com.rhitam.jobtracker.application.Stage;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateApplicationRequest(
        @NotBlank @Size(max = 200) String company,
        @NotBlank @Size(max = 200) String role,
        @Size(max = 1000) String jobUrl,
        String notes,
        Stage stage,
        @Email @Size(max = 320) String contactEmail) {
}
