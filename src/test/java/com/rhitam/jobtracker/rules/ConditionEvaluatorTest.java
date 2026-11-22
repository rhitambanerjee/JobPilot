package com.rhitam.jobtracker.rules;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rhitam.jobtracker.rules.model.Condition;
import com.rhitam.jobtracker.rules.model.Operator;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ConditionEvaluatorTest {

    private final ConditionEvaluator evaluator = new ConditionEvaluator();

    private final Map<String, Object> payload = Map.of(
            "toStage", "INTERVIEW",
            "company", "Rippling Inc",
            "daysIdle", 21,
            "tags", List.of("backend", "remote"),
            "meta", Map.of("source", "referral"));

    @Test
    void eqIsCaseInsensitiveForStrings() {
        assertTrue(evaluator.matches(new Condition("toStage", Operator.EQ, "interview"), payload));
        assertFalse(evaluator.matches(new Condition("toStage", Operator.EQ, "OFFER"), payload));
    }

    @Test
    void eqComparesNumbersByValue() {
        assertTrue(evaluator.matches(new Condition("daysIdle", Operator.EQ, 21.0), payload));
    }

    @Test
    void inMatchesAnyOption() {
        assertTrue(evaluator.matches(new Condition("toStage", Operator.IN, List.of("OFFER", "INTERVIEW")), payload));
        assertFalse(evaluator.matches(new Condition("toStage", Operator.IN, List.of("OFFER")), payload));
        assertFalse(evaluator.matches(new Condition("toStage", Operator.IN, "INTERVIEW"), payload));
    }

    @Test
    void gtNeedsNumbersOnBothSides() {
        assertTrue(evaluator.matches(new Condition("daysIdle", Operator.GT, 14), payload));
        assertFalse(evaluator.matches(new Condition("daysIdle", Operator.GT, 21), payload));
        assertFalse(evaluator.matches(new Condition("company", Operator.GT, 1), payload));
    }

    @Test
    void containsWorksOnStringsAndLists() {
        assertTrue(evaluator.matches(new Condition("company", Operator.CONTAINS, "rippling"), payload));
        assertTrue(evaluator.matches(new Condition("tags", Operator.CONTAINS, "remote"), payload));
        assertFalse(evaluator.matches(new Condition("tags", Operator.CONTAINS, "onsite"), payload));
    }

    @Test
    void dottedPathsReachNestedFields() {
        assertTrue(evaluator.matches(new Condition("meta.source", Operator.EQ, "referral"), payload));
    }

    @Test
    void missingFieldNeverMatches() {
        assertFalse(evaluator.matches(new Condition("nope", Operator.EQ, "x"), payload));
    }

    @Test
    void allMatchIsAnd_andEmptyListMatches() {
        assertTrue(evaluator.allMatch(List.of(), payload));
        assertTrue(evaluator.allMatch(List.of(
                new Condition("toStage", Operator.EQ, "INTERVIEW"),
                new Condition("daysIdle", Operator.GT, 1)), payload));
        assertFalse(evaluator.allMatch(List.of(
                new Condition("toStage", Operator.EQ, "INTERVIEW"),
                new Condition("daysIdle", Operator.GT, 100)), payload));
    }

    @Test
    void gteIncludesTheBoundary() {
        assertTrue(evaluator.matches(new Condition("daysIdle", Operator.GTE, 21), payload));
        assertTrue(evaluator.matches(new Condition("daysIdle", Operator.GTE, 7), payload));
        assertFalse(evaluator.matches(new Condition("daysIdle", Operator.GTE, 22), payload));
    }
}
