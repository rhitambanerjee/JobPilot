package com.rhitam.jobtracker.rules;

import com.rhitam.jobtracker.auth.AuthenticatedUser;
import com.rhitam.jobtracker.common.exception.NotFoundException;
import com.rhitam.jobtracker.rules.model.RuleRequest;
import jakarta.validation.Valid;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rules")
public class RuleController {

    private final RuleRepository repository;
    private final RuleValidator validator;
    private final Clock clock;

    public RuleController(RuleRepository repository, RuleValidator validator, Clock clock) {
        this.repository = repository;
        this.validator = validator;
        this.clock = clock;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Rule create(@AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody RuleRequest request) {
        validator.validate(user.id(), request);
        return repository.save(new Rule(user.id(), request.name(), request.trigger(), request.conditions(),
                request.actions(), request.enabled(), clock.instant()));
    }

    @GetMapping
    public List<Rule> list(@AuthenticationPrincipal AuthenticatedUser user) {
        return repository.findByUserIdOrderByCreatedAtDesc(user.id());
    }

    @PostMapping("/{id}/enable")
    @Transactional
    public Rule enable(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        return setEnabled(user.id(), id, true);
    }

    @PostMapping("/{id}/disable")
    @Transactional
    public Rule disable(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        return setEnabled(user.id(), id, false);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        repository.delete(find(user.id(), id));
    }

    private Rule setEnabled(UUID userId, UUID id, boolean enabled) {
        Rule rule = find(userId, id);
        rule.setEnabled(enabled);
        return rule;
    }

    private Rule find(UUID userId, UUID id) {
        return repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Rule " + id + " not found"));
    }
}
