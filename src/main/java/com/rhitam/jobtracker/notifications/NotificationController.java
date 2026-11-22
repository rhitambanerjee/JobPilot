package com.rhitam.jobtracker.notifications;

import com.rhitam.jobtracker.auth.AuthenticatedUser;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    /** The latest 100, newest first. Unread ones have readAt == null. */
    @GetMapping
    public List<Notification> list(@AuthenticationPrincipal AuthenticatedUser user) {
        return service.latest(user.id());
    }

    @PostMapping("/{id}/read")
    public Notification markRead(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        return service.markRead(user.id(), id);
    }

    @PostMapping("/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markAllRead(@AuthenticationPrincipal AuthenticatedUser user) {
        service.markAllRead(user.id());
    }
}
