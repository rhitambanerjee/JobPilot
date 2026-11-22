package com.rhitam.jobtracker.emails;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TemplateRendererTest {

    @Test
    void findsPlaceholdersInOrderWithoutDuplicates() {
        assertEquals(List.of("role", "company", "yourName"),
                TemplateRenderer.placeholders("Hi {{role}} at {{company}}", "{{ yourName }} {{role}}"));
    }

    @Test
    void separatesBuiltInFromUserPlaceholders() {
        assertEquals(List.of("recruiterName", "yourName"),
                TemplateRenderer.customPlaceholders("{{company}} {{recruiterName}}", "{{role}} {{yourName}}"));
    }

    @Test
    void rendersValuesAndLeavesUnknownPlaceholdersVisible() {
        assertEquals("Hello Priya, re {{role}}",
                TemplateRenderer.render("Hello {{name}}, re {{role}}", Map.of("name", "Priya")));
    }

    @Test
    void valuesWithDollarSignsAndBackslashesAreInsertedLiterally() {
        assertEquals("Salary $5 \\1", TemplateRenderer.render("Salary {{x}}", Map.of("x", "$5 \\1")));
    }

    @Test
    void reportsMissingValues() {
        assertEquals(List.of("yourName"),
                TemplateRenderer.missing(Map.of("company", "Acme"), "{{company}}", "Thanks, {{yourName}}"));
    }

    @Test
    void detectsMalformedPlaceholders() {
        assertTrue(TemplateRenderer.isMalformed("Hi {{ }}"));
        assertTrue(TemplateRenderer.isMalformed("Hi {{1abc}}"));
        assertTrue(TemplateRenderer.isMalformed("stray }} brace"));
        assertFalse(TemplateRenderer.isMalformed("Hi {{ok}} and {{also_ok2}}"));
    }
}
