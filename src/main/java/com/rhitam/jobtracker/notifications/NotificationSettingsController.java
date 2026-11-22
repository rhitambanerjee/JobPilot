package com.rhitam.jobtracker.notifications;

import com.rhitam.jobtracker.auth.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notification-settings")
public class NotificationSettingsController {

    public record SettingsRequest(@Email @Size(max = 320) String email, boolean emailEnabled) {
    }

    public record SettingsResponse(String email, boolean emailEnabled) {
        static SettingsResponse from(NotificationSettings s) {
            return new SettingsResponse(s.getEmail(), s.isEmailEnabled());
        }
    }

    private final NotificationService service;

    public NotificationSettingsController(NotificationService service) {
        this.service = service;
    }

    @GetMapping
    public SettingsResponse get(@AuthenticationPrincipal AuthenticatedUser user) {
        return SettingsResponse.from(service.getSettings(user.id()));
    }

    @PutMapping
    public SettingsResponse save(@AuthenticationPrincipal AuthenticatedUser user,
                                 @Valid @RequestBody SettingsRequest request) {
        return SettingsResponse.from(service.saveSettings(user.id(), request.email(), request.emailEnabled()));
    }

    /** Creates a test notification (and emails it). */
    @PostMapping("/test")
    @ResponseStatus(HttpStatus.CREATED)
    public Notification sendTest(@AuthenticationPrincipal AuthenticatedUser user) {
        return service.sendTest(user.id());
    }
}
