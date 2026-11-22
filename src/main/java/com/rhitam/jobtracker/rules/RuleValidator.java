package com.rhitam.jobtracker.rules;

import com.rhitam.jobtracker.actions.ActionRegistry;
import com.rhitam.jobtracker.common.exception.BadRequestException;
import com.rhitam.jobtracker.outbox.EventTypes;
import com.rhitam.jobtracker.rules.model.ActionSpec;
import com.rhitam.jobtracker.rules.model.Condition;
import com.rhitam.jobtracker.rules.model.RuleRequest;
import java.util.Collection;
import org.springframework.stereotype.Component;

/** Rejects bad rules at write time so they can't fail later inside the poller. */
@Component
public class RuleValidator {

    private final ActionRegistry registry;

    public RuleValidator(ActionRegistry registry) {
        this.registry = registry;
    }

    public void validate(RuleRequest request) {
        String eventType = request.trigger().eventType();
        if (!EventTypes.ALL.contains(eventType)) {
            throw new BadRequestException("Unknown trigger event '" + eventType + "'. Known: " + EventTypes.ALL);
        }
        for (Condition condition : request.conditions()) {
            switch (condition.op()) {
                case IN -> {
                    if (!(condition.value() instanceof Collection<?>)) {
                        throw new BadRequestException("Condition on '" + condition.field() + "': 'in' needs a list");
                    }
                }
                case GT, GTE -> {
                    if (!(condition.value() instanceof Number)) {
                        throw new BadRequestException("Condition on '" + condition.field() + "': '" + condition.op().json() + "' needs a number");
                    }
                }
                default -> {
                    if (condition.value() == null) {
                        throw new BadRequestException("Condition on '" + condition.field() + "' needs a value");
                    }
                }
            }
        }
        for (ActionSpec action : request.actions()) {
            if (!registry.supports(action.type())) {
                throw new BadRequestException(
                        "Unknown action '" + action.type() + "'. Known: " + registry.types());
            }
            registry.get(action.type()).validate(action.params());
        }
    }
}
