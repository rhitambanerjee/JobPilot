package com.rhitam.jobtracker.emails;

import com.rhitam.jobtracker.auth.AuthenticatedUser;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/outgoing-emails")
public class OutgoingEmailController {

    private final OutgoingEmailRepository repository;

    public OutgoingEmailController(OutgoingEmailRepository repository) {
        this.repository = repository;
    }

    /** Emails the app sent (or tried to send) for one application, newest first. */
    @GetMapping
    public List<OutgoingEmail> forApplication(@AuthenticationPrincipal AuthenticatedUser user,
                                              @RequestParam UUID applicationId) {
        return repository.findByApplicationIdAndUserIdOrderByCreatedAtDesc(applicationId, user.id());
    }
}
