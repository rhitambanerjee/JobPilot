package com.rhitam.jobtracker.audit;

import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audit")
public class AuditController {

    private final AuditRepository repository;

    public AuditController(AuditRepository repository) {
        this.repository = repository;
    }

    /** GET /api/audit?entityId=... for one application's history, or the latest 100 entries without a filter. */
    @GetMapping
    public List<AuditLog> list(@RequestParam(required = false) UUID entityId) {
        return entityId == null
                ? repository.findTop100ByOrderByCreatedAtDesc()
                : repository.findByEntityIdOrderByCreatedAtDesc(entityId);
    }
}
