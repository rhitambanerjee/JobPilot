package com.rhitam.jobtracker.application.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/** A blank or null address clears the saved contact. */
public record UpdateContactEmailRequest(@Email @Size(max = 320) String contactEmail) {
}
