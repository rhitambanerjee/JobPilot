package com.rhitam.jobtracker.actions.checklist;

import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/checklists")
public class ChecklistController {

    private final ChecklistRepository repository;

    public ChecklistController(ChecklistRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<Checklist> forApplication(@RequestParam UUID applicationId) {
        return repository.findByApplicationId(applicationId);
    }
}
