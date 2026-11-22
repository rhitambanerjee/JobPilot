package com.rhitam.jobtracker.emails;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pure helpers for {{placeholder}} templates. No Spring, no I/O. */
public final class TemplateRenderer {

    /** Filled in automatically from the application. Any other placeholder is supplied by the user. */
    public static final List<String> BUILT_IN =
            List.of("company", "role", "stage", "daysIdle", "appliedDate", "jobUrl");

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([A-Za-z][A-Za-z0-9_]*)\\s*\\}\\}");

    private TemplateRenderer() {
    }

    /** Every placeholder name in the given texts, in order of first appearance, without duplicates. */
    public static List<String> placeholders(String... texts) {
        Set<String> found = new LinkedHashSet<>();
        for (String text : texts) {
            Matcher m = PLACEHOLDER.matcher(text);
            while (m.find()) {
                found.add(m.group(1));
            }
        }
        return new ArrayList<>(found);
    }

    /** Placeholders the user has to fill in (everything that is not built in). */
    public static List<String> customPlaceholders(String... texts) {
        return placeholders(texts).stream().filter(name -> !BUILT_IN.contains(name)).toList();
    }

    /** True for leftovers like "{{ }}" or "{{1abc}}" that would otherwise be sent literally. */
    public static boolean isMalformed(String text) {
        String rest = PLACEHOLDER.matcher(text).replaceAll("");
        return rest.contains("{{") || rest.contains("}}");
    }

    /** Placeholders used in the texts that have no value. */
    public static List<String> missing(Map<String, String> values, String... texts) {
        return placeholders(texts).stream().filter(name -> values.get(name) == null).toList();
    }

    public static String render(String text, Map<String, String> values) {
        Matcher m = PLACEHOLDER.matcher(text);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String value = values.get(m.group(1));
            m.appendReplacement(out, Matcher.quoteReplacement(value == null ? m.group() : value));
        }
        m.appendTail(out);
        return out.toString();
    }
}
