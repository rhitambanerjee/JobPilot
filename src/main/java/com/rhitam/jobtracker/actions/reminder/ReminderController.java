package com.rhitam.jobtracker.actions.reminder;

import com.rhitam.jobtracker.auth.AuthenticatedUser;
import com.rhitam.jobtracker.common.exception.NotFoundException;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reminders")
public class ReminderController {

    private final ReminderRepository repository;
    private final Clock clock;

    public ReminderController(ReminderRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /**
     * GET /api/reminders?applicationId=...  -> every reminder for one application
     * GET /api/reminders?due=true           -> open reminders that are due now or overdue
     * GET /api/reminders                    -> all open reminders
     */
    @GetMapping
    public List<Reminder> list(@AuthenticationPrincipal AuthenticatedUser user,
                               @RequestParam(required = false) UUID applicationId,
                               @RequestParam(defaultValue = "false") boolean due) {
        if (applicationId != null) {
            return repository.findByApplicationIdAndUserId(applicationId, user.id());
        }
        return due
                ? repository.findByUserIdAndDoneFalseAndDueAtBeforeOrderByDueAt(user.id(), clock.instant())
                : repository.findByUserIdAndDoneFalseOrderByDueAt(user.id());
    }

    @PostMapping("/{id}/complete")
    @Transactional
    public Reminder complete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        Reminder reminder = repository.findByIdAndUserId(id, user.id())
                .orElseThrow(() -> new NotFoundException("Reminder " + id + " not found"));
        reminder.complete();
        return reminder;
    }
}
