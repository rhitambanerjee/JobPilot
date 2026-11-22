package com.rhitam.jobtracker.actions;

import com.rhitam.jobtracker.actions.checklist.Checklist;
import com.rhitam.jobtracker.actions.checklist.ChecklistRepository;
import com.rhitam.jobtracker.common.exception.BadRequestException;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** params: title (string, optional), items (list of strings, optional). */
@Component
public class CreateChecklistHandler implements ActionHandler {

    public static final String TYPE = "create_checklist";

    private static final List<String> DEFAULT_ITEMS =
            List.of("Research the company", "Tailor the resume", "Prepare stories for common questions");

    private final ChecklistRepository repository;
    private final Clock clock;

    public CreateChecklistHandler(ChecklistRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public void validate(Map<String, Object> params) {
        Object items = params.get("items");
        if (items != null && !(items instanceof List<?> list && list.stream().allMatch(String.class::isInstance))) {
            throw new BadRequestException("create_checklist: 'items' must be a list of strings");
        }
    }

    @Override
    public void execute(ActionContext context) {
        Map<String, Object> params = context.params();
        String title = params.get("title") instanceof String t && !t.isBlank() ? t : "Prep checklist";
        List<String> items = params.get("items") instanceof List<?> raw
                ? raw.stream().map(String::valueOf).toList()
                : DEFAULT_ITEMS;
        repository.save(new Checklist(context.applicationId(), title, items, clock.instant()));
    }
}
