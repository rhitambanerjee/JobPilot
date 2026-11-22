package com.rhitam.jobtracker.rules.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Serialised in lower case: "eq", "in", "gt", "gte", "contains". */
public enum Operator {
    EQ, IN, GT, GTE, CONTAINS;

    @JsonCreator
    public static Operator from(String value) {
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException("Unknown operator '" + value + "'. Use eq, in, gt, gte or contains");
        }
    }

    @JsonValue
    public String json() {
        return name().toLowerCase();
    }
}
