package com.rhitam.jobtracker.actions.reminder;

import com.rhitam.jobtracker.common.exception.NotFoundException;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
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
     * GET /api/reminders?due=true           -> open reminders that are due now
     * GET /api/reminders                    -> all open reminders
     */
    @GetMapping
    public List<Reminder> list(@RequestParam(required = false) UUID applicationId,
                               @RequestParam(defaultValue = "false") boolean due) {
        if (applicationId != null) {
            return repository.findByApplicationId(applicationId);
        }
        return due
                ? repository.findByDoneFalseAndDueAtBeforeOrderByDueAt(clock.instant())
                : repository.findByDoneFalseOrderByDueAt();
    }

    @PostMapping("/{id}/complete")
    @Transactional
    public Reminder complete(@PathVariable UUID id) {
        Reminder reminder = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Reminder " + id + " not found"));
        reminder.complete();
        return reminder;
    }
}
