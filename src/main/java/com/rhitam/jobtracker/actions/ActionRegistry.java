package com.rhitam.jobtracker.actions;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Spring injects every ActionHandler bean; the registry just indexes them by type. */
@Component
public class ActionRegistry {

    private final Map<String, ActionHandler> handlers;

    public ActionRegistry(List<ActionHandler> handlerBeans) {
        this.handlers = handlerBeans.stream()
                .collect(Collectors.toUnmodifiableMap(ActionHandler::type, Function.identity()));
    }

    public boolean supports(String type) {
        return handlers.containsKey(type);
    }

    public ActionHandler get(String type) {
        ActionHandler handler = handlers.get(type);
        if (handler == null) {
            throw new IllegalArgumentException("No handler registered for action type '" + type + "'");
        }
        return handler;
    }

    public Set<String> types() {
        return handlers.keySet();
    }
}
