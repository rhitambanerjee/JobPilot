package com.rhitam.jobtracker.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rhitam.jobtracker.jobs.JobQClient;
import com.rhitam.jobtracker.jobs.JobRequest;
import com.rhitam.jobtracker.outbox.EventTypes;
import com.rhitam.jobtracker.outbox.OutboxEvent;
import com.rhitam.jobtracker.rules.model.ActionSpec;
import com.rhitam.jobtracker.rules.model.Condition;
import com.rhitam.jobtracker.rules.model.Operator;
import com.rhitam.jobtracker.rules.model.Trigger;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class RuleEngineTest {

    private final RuleRepository rules = mock(RuleRepository.class);
    private final JobQClient jobQ = mock(JobQClient.class);
    private final RuleEngine engine = new RuleEngine(rules, new ConditionEvaluator(), jobQ);

    private final UUID appId = UUID.randomUUID();
    private OutboxEvent interviewEvent;

    @BeforeEach
    void setUp() {
        interviewEvent = new OutboxEvent("APPLICATION", appId, EventTypes.APPLICATION_STAGE_CHANGED,
                Map.of("toStage", "INTERVIEW"), Instant.now());
        ReflectionTestUtils.setField(interviewEvent, "id", UUID.randomUUID());
        when(jobQ.enqueue(any())).thenReturn(true);
    }

    private Rule rule(String eventType, List<Condition> conditions, List<ActionSpec> actions) {
        Rule rule = new Rule("r", new Trigger(eventType), conditions, actions, true, Instant.now());
        ReflectionTestUtils.setField(rule, "id", UUID.randomUUID());
        return rule;
    }

    @Test
    void matchingRuleEnqueuesOneJobPerAction() {
        Rule rule = rule(EventTypes.APPLICATION_STAGE_CHANGED,
                List.of(new Condition("toStage", Operator.EQ, "INTERVIEW")),
                List.of(new ActionSpec("create_checklist", Map.of()), new ActionSpec("create_reminder", Map.of())));
        when(rules.findByEnabledTrue()).thenReturn(List.of(rule));

        assertEquals(2, engine.handle(interviewEvent));

        ArgumentCaptor<JobRequest> captor = ArgumentCaptor.forClass(JobRequest.class);
        verify(jobQ, org.mockito.Mockito.times(2)).enqueue(captor.capture());
        List<JobRequest> sent = captor.getAllValues();
        assertEquals(rule.getId() + ":" + interviewEvent.getId() + ":0", sent.get(0).idempotencyKey());
        assertEquals(rule.getId() + ":" + interviewEvent.getId() + ":1", sent.get(1).idempotencyKey());
        assertEquals(appId, sent.get(0).applicationId());
    }

    @Test
    void ruleForAnotherEventTypeIsIgnored() {
        Rule rule = rule(EventTypes.APPLICATION_STALE, List.of(), List.of(new ActionSpec("create_reminder", Map.of())));
        when(rules.findByEnabledTrue()).thenReturn(List.of(rule));

        assertEquals(0, engine.handle(interviewEvent));
        verify(jobQ, never()).enqueue(any());
    }

    @Test
    void failingConditionSkipsTheRule() {
        Rule rule = rule(EventTypes.APPLICATION_STAGE_CHANGED,
                List.of(new Condition("toStage", Operator.EQ, "OFFER")),
                List.of(new ActionSpec("create_reminder", Map.of())));
        when(rules.findByEnabledTrue()).thenReturn(List.of(rule));

        assertEquals(0, engine.handle(interviewEvent));
        verify(jobQ, never()).enqueue(any());
    }

    @Test
    void duplicateEnqueueIsNotCounted() {
        Rule rule = rule(EventTypes.APPLICATION_STAGE_CHANGED, List.of(),
                List.of(new ActionSpec("create_reminder", Map.of())));
        when(rules.findByEnabledTrue()).thenReturn(List.of(rule));
        when(jobQ.enqueue(any())).thenReturn(false);

        assertEquals(0, engine.handle(interviewEvent));
    }

    private OutboxEvent staleEvent(String dedupeKey) {
        OutboxEvent event = new OutboxEvent("APPLICATION", appId, EventTypes.APPLICATION_STALE,
                Map.of("dedupeKey", dedupeKey), Instant.now());
        ReflectionTestUtils.setField(event, "id", UUID.randomUUID());
        return event;
    }

    @Test
    void eventsSharingADedupeKeyProduceTheSameJobKey() {
        Rule rule = rule(EventTypes.APPLICATION_STALE, List.of(), List.of(new ActionSpec("create_reminder", Map.of())));
        when(rules.findByEnabledTrue()).thenReturn(List.of(rule));

        engine.handle(staleEvent("stale:app1:123"));   // day 7
        engine.handle(staleEvent("stale:app1:123"));   // day 8, same idle stretch, different event id

        ArgumentCaptor<JobRequest> captor = ArgumentCaptor.forClass(JobRequest.class);
        verify(jobQ, org.mockito.Mockito.times(2)).enqueue(captor.capture());
        assertEquals(captor.getAllValues().get(0).idempotencyKey(), captor.getAllValues().get(1).idempotencyKey());
    }
}
