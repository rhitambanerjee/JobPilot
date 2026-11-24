package com.rhitam.jobtracker.analytics;

import com.rhitam.jobtracker.analytics.FunnelReport.StageCount;
import com.rhitam.jobtracker.analytics.FunnelReport.Step;
import com.rhitam.jobtracker.analytics.FunnelReport.WeekCount;
import com.rhitam.jobtracker.application.Application;
import com.rhitam.jobtracker.application.Stage;
import com.rhitam.jobtracker.audit.AuditLog;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Turns applications plus their audit history into the funnel report. Pure: no database, no clock, no Spring,
 * so every number can be checked by a plain unit test.
 *
 * Definitions (also shown to the user on the Insights page):
 *  - "reached" a stage = at some point the application was in it, even if it later moved on or was archived.
 *    Reached is cumulative: getting to Interview also counts as having got past Screening, because stages can be skipped.
 *  - "responded" = reached Screening or later, or was rejected after applying.
 *  - times are medians, which a single very slow reply can't distort.
 */
public final class FunnelCalculator {

    /** The path an application travels when things go well. */
    static final List<Stage> PATH =
            List.of(Stage.APPLIED, Stage.SCREENING, Stage.INTERVIEW, Stage.OFFER, Stage.ACCEPTED);

    private static final List<Stage> REJECTABLE = List.of(Stage.APPLIED, Stage.SCREENING, Stage.INTERVIEW, Stage.OFFER);

    private FunnelCalculator() {
    }

    public static FunnelReport calculate(List<Application> applications, List<AuditLog> events, Instant now, int weeks) {
        Map<UUID, List<AuditLog>> eventsByApplication =
                events.stream().collect(Collectors.groupingBy(AuditLog::getEntityId));
        List<Journey> journeys = applications.stream()
                .map(a -> Journey.of(a, eventsByApplication.getOrDefault(a.getId(), List.of())))
                .toList();
        List<Journey> appliedJourneys = journeys.stream().filter(j -> j.maxIndex() >= 0).toList();
        int applied = appliedJourneys.size();

        List<Step> funnel = new ArrayList<>();
        int previous = 0;
        for (int i = 0; i < PATH.size(); i++) {
            final int index = i;
            List<Journey> reached = appliedJourneys.stream().filter(j -> j.maxIndex() >= index).toList();
            int count = reached.size();
            Double percentOfApplied = applied == 0 ? null : round(100.0 * count / applied);
            Double conversion = (i == 0 || previous == 0) ? null : round(100.0 * count / previous);
            Double medianDays = i == 0 ? null : medianDays(reached.stream()
                    .map(j -> daysBetween(j.appliedAt(), j.reachedAtOrAfter(index)))
                    .filter(Objects::nonNull)
                    .toList());
            funnel.add(new Step(PATH.get(i).name(), count, percentOfApplied, conversion, medianDays));
            previous = count;
        }

        int responded = (int) appliedJourneys.stream().filter(j -> j.maxIndex() >= 1 || j.rejectedFrom() != null).count();
        Double responseRate = applied == 0 ? null : round(100.0 * responded / applied);
        Double medianFirstResponse = medianDays(appliedJourneys.stream()
                .map(j -> daysBetween(j.appliedAt(), j.firstResponseAt()))
                .filter(Objects::nonNull)
                .toList());

        List<StageCount> rejectedAfter = REJECTABLE.stream()
                .map(stage -> new StageCount(stage.name(),
                        (int) journeys.stream().filter(j -> j.rejectedFrom() == stage).count()))
                .toList();

        List<StageCount> currentByStage = new ArrayList<>();
        for (Stage stage : Stage.values()) {
            currentByStage.add(new StageCount(stage.name(),
                    (int) applications.stream().filter(a -> a.getStage() == stage).count()));
        }
        int wishlist = (int) applications.stream().filter(a -> a.getStage() == Stage.WISHLIST).count();

        return new FunnelReport(applications.size(), wishlist, applied, responded, responseRate, medianFirstResponse,
                funnel, rejectedAfter, currentByStage, weekly(appliedJourneys, now, weeks));
    }

    /** Applications sent per week (weeks start on Monday, UTC), oldest first, zero-filled, ending with this week. */
    private static List<WeekCount> weekly(List<Journey> appliedJourneys, Instant now, int weeks) {
        Map<LocalDate, Integer> perWeek = new HashMap<>();
        for (Journey journey : appliedJourneys) {
            if (journey.appliedAt() != null) {
                perWeek.merge(weekStart(journey.appliedAt()), 1, Integer::sum);
            }
        }
        LocalDate thisWeek = weekStart(now);
        List<WeekCount> result = new ArrayList<>();
        for (int w = weeks - 1; w >= 0; w--) {
            LocalDate start = thisWeek.minusWeeks(w);
            result.add(new WeekCount(start, perWeek.getOrDefault(start, 0)));
        }
        return result;
    }

    private static LocalDate weekStart(Instant instant) {
        return LocalDate.ofInstant(instant, ZoneOffset.UTC).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private static Double daysBetween(Instant from, Instant to) {
        if (from == null || to == null) {
            return null;
        }
        return Duration.between(from, to).toMillis() / 86_400_000.0;
    }

    private static Double medianDays(List<Double> values) {
        if (values.isEmpty()) {
            return null;
        }
        List<Double> sorted = values.stream().sorted().toList();
        int n = sorted.size();
        double median = n % 2 == 1 ? sorted.get(n / 2) : (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2.0;
        return round(median);
    }

    private static double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    /** What happened to one application: when it first reached each stage, and where it was when rejected. */
    private record Journey(Map<Stage, Instant> firstReached, Stage rejectedFrom) {

        static Journey of(Application application, List<AuditLog> events) {
            Map<Stage, Instant> reached = new EnumMap<>(Stage.class);
            Stage rejectedFrom = null;
            List<AuditLog> ordered = events.stream().sorted(Comparator.comparing(AuditLog::getCreatedAt)).toList();
            for (AuditLog event : ordered) {
                Map<String, Object> details = event.getDetails();
                if ("CREATED".equals(event.getAction())) {
                    stageOf(details.get("stage")).ifPresent(s -> reached.putIfAbsent(s, event.getCreatedAt()));
                } else if ("STAGE_CHANGED".equals(event.getAction())) {
                    Optional<Stage> to = stageOf(details.get("to"));
                    to.ifPresent(s -> reached.putIfAbsent(s, event.getCreatedAt()));
                    if (rejectedFrom == null && to.orElse(null) == Stage.REJECTED) {
                        rejectedFrom = stageOf(details.get("from")).orElse(null);
                    }
                }
            }
            if (reached.isEmpty()) {
                // no history recorded: all we know is where it is now
                reached.put(application.getStage(), application.getCreatedAt());
            }
            return new Journey(reached, rejectedFrom);
        }

        private static Optional<Stage> stageOf(Object value) {
            if (value instanceof String name) {
                try {
                    return Optional.of(Stage.valueOf(name));
                } catch (IllegalArgumentException ignored) {
                    return Optional.empty();
                }
            }
            return Optional.empty();
        }

        Instant appliedAt() {
            return firstReached.get(Stage.APPLIED);
        }

        /** Index of the furthest PATH stage reached, or -1 if it never got past the wishlist. */
        int maxIndex() {
            for (int i = PATH.size() - 1; i >= 0; i--) {
                if (firstReached.containsKey(PATH.get(i))) {
                    return i;
                }
            }
            return -1;
        }

        /** When it was first at this PATH stage or any later one (a skipped stage counts from the stage that skipped it). */
        Instant reachedAtOrAfter(int index) {
            Instant earliest = null;
            for (int i = index; i < PATH.size(); i++) {
                Instant at = firstReached.get(PATH.get(i));
                if (at != null && (earliest == null || at.isBefore(earliest))) {
                    earliest = at;
                }
            }
            return earliest;
        }

        /** The first sign of life after applying: any later stage, or a rejection. */
        Instant firstResponseAt() {
            Instant earliest = reachedAtOrAfter(1);
            Instant rejected = firstReached.get(Stage.REJECTED);
            if (rejected != null && (earliest == null || rejected.isBefore(earliest))) {
                return rejected;
            }
            return earliest;
        }
    }
}
