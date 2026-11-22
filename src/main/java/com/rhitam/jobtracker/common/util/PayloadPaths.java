package com.rhitam.jobtracker.common.util;

import java.util.Map;

/** Reads dotted paths ("a.b.c") out of nested JSON-like maps. */
public final class PayloadPaths {

    private PayloadPaths() {
    }

    public static Object get(Map<String, Object> root, String path) {
        if (root == null || path == null || path.isBlank()) {
            return null;
        }
        Object current = root;
        for (String part : path.split("\\.")) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = map.get(part);
        }
        return current;
    }
}
