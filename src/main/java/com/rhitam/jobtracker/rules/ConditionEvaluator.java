package com.rhitam.jobtracker.rules;

import com.rhitam.jobtracker.common.util.PayloadPaths;
import com.rhitam.jobtracker.rules.model.Condition;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Pure function of (condition, payload): no I/O, trivially unit-testable. */
@Component
public class ConditionEvaluator {

    /** All conditions must hold (AND). An empty list always matches. */
    public boolean allMatch(List<Condition> conditions, Map<String, Object> payload) {
        return conditions.stream().allMatch(c -> matches(c, payload));
    }

    public boolean matches(Condition condition, Map<String, Object> payload) {
        Object actual = PayloadPaths.get(payload, condition.field());
        Object expected = condition.value();
        if (actual == null) {
            return false;   // a missing field never matches, whatever the operator
        }
        return switch (condition.op()) {
            case EQ -> looselyEqual(actual, expected);
            case IN -> expected instanceof Collection<?> options
                    && options.stream().anyMatch(option -> looselyEqual(actual, option));
            case GT -> {
                Double a = toNumber(actual);
                Double e = toNumber(expected);
                yield a != null && e != null && a > e;
            }
            case GTE -> {
                Double a = toNumber(actual);
                Double e = toNumber(expected);
                yield a != null && e != null && a >= e;
            }
            case CONTAINS -> actual instanceof Collection<?> items
                    ? items.stream().anyMatch(item -> looselyEqual(item, expected))
                    : expected != null && String.valueOf(actual).toLowerCase()
                            .contains(String.valueOf(expected).toLowerCase());
        };
    }

    private static boolean looselyEqual(Object a, Object b) {
        if (a == null || b == null) {
            return false;
        }
        Double na = toNumber(a);
        Double nb = toNumber(b);
        if (na != null && nb != null) {
            return na.doubleValue() == nb.doubleValue();
        }
        return String.valueOf(a).equalsIgnoreCase(String.valueOf(b));
    }

    private static Double toNumber(Object value) {
        return value instanceof Number n ? n.doubleValue() : null;
    }
}
