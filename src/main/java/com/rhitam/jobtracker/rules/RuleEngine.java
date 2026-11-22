package com.rhitam.jobtracker.rules;

import com.rhitam.jobtracker.jobs.JobQClient;
import com.rhitam.jobtracker.jobs.JobRequest;
import com.rhitam.jobtracker.outbox.OutboxEvent;
import com.rhitam.jobtracker.rules.model.ActionSpec;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Decides WHAT should happen for an event; it never does the work itself. Matching rules become queued
 * jobs, which keeps event handling fast and lets the worker retry independently.
 */
@Service
public class RuleEngine {

    private static final Logger log = LoggerFactory.getLogger(RuleEngine.class);

    private final RuleRepository rules;
    private final ConditionEvaluator evaluator;
    private final JobQClient jobQ;

    public RuleEngine(RuleRepository rules, ConditionEvaluator evaluator, JobQClient jobQ) {
        this.rules = rules;
        this.evaluator = evaluator;
        this.jobQ = jobQ;
    }

    /** @return how many NEW jobs were enqueued (duplicates from re-delivery don't count) */
    public int handle(OutboxEvent event) {
        int enqueued = 0;
        for (Rule rule : rules.findByUserIdAndEnabledTrue(event.getUserId())) {
            if (!rule.getTrigger().eventType().equals(event.getEventType())) {
                continue;
            }
            if (!evaluator.allMatch(rule.getConditions(), event.getPayload())) {
                continue;
            }
            List<ActionSpec> actions = rule.getActions();
            for (int i = 0; i < actions.size(); i++) {
                ActionSpec action = actions.get(i);
                // rule + dedupe source + position => same input always yields the same key.
                // Events may carry a "dedupeKey" (e.g. one idle stretch of one application) so that a rule
                // fires once per stretch even if the event is emitted every day.
                String dedupe = event.getPayload() != null && event.getPayload().get("dedupeKey") instanceof String k
                        ? k : String.valueOf(event.getId());
                String key = rule.getId() + ":" + dedupe + ":" + i;
                boolean isNew = jobQ.enqueue(new JobRequest(
                        event.getUserId(), key, rule.getId(), event.getAggregateId(), action.type(), action.params()));
                if (isNew) {
                    enqueued++;
                    log.debug("Rule '{}' queued {} for {}", rule.getName(), action.type(), event.getAggregateId());
                }
            }
        }
        return enqueued;
    }
}
