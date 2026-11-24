package com.rhitam.jobtracker.analytics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.rhitam.jobtracker.analytics.FunnelReport.Step;
import com.rhitam.jobtracker.application.Application;
import com.rhitam.jobtracker.application.Stage;
import com.rhitam.jobtracker.audit.AuditLog;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class FunnelCalculatorTest {

    private static final Instant T0 = Instant.parse("2026-11-02T00:00:00Z");   // a Monday
    private static final Instant NOW = Instant.parse("2026-11-16T12:00:00Z");  // Monday two weeks later
    private final UUID userId = UUID.randomUUID();
    private final List<Application> applications = new ArrayList<>();
    private final List<AuditLog> events = new ArrayList<>();

    private static Instant days(long n) {
        return T0.plus(Duration.ofDays(n));
    }

    /** An application created at the given stage and day, recorded exactly like ApplicationService does. */
    private Application create(Stage stage, long day) {
        Application app = new Application(userId, "Acme", "Engineer", null, null, stage, days(day));
        ReflectionTestUtils.setField(app, "id", UUID.randomUUID());
        applications.add(app);
        events.add(AuditLog.of(userId, "APPLICATION", app.getId(), "CREATED", "user", Map.of("stage", stage.name()), days(day)));
        return app;
    }

    private void move(Application app, Stage to, long day) {
        Stage from = app.moveTo(to, days(day));
        events.add(AuditLog.of(userId, "APPLICATION", app.getId(), "STAGE_CHANGED", "user",
                Map.of("from", from.name(), "to", to.name()), days(day)));
    }

    private FunnelReport scenario() {
        Application a = create(Stage.APPLIED, 0);          // got an offer
        move(a, Stage.SCREENING, 3);
        move(a, Stage.INTERVIEW, 10);
        move(a, Stage.OFFER, 20);

        Application b = create(Stage.APPLIED, 0);          // rejected straight away
        move(b, Stage.REJECTED, 5);

        Application c = create(Stage.APPLIED, 7);          // skipped screening
        move(c, Stage.INTERVIEW, 14);

        create(Stage.APPLIED, 7);                          // d: heard nothing
        create(Stage.WISHLIST, 0);                         // e: not applied yet

        return FunnelCalculator.calculate(applications, events, NOW, 3);
    }

    private static Step step(FunnelReport report, Stage stage) {
        return report.funnel().stream().filter(s -> s.stage().equals(stage.name())).findFirst().orElseThrow();
    }

    @Test
    void countsApplicationsThatEverReachedEachStage_includingSkippedOnes() {
        FunnelReport report = scenario();

        assertEquals(5, report.totalApplications());
        assertEquals(1, report.wishlist());
        assertEquals(4, report.applied());                       // the wishlist item never applied
        assertEquals(4, step(report, Stage.APPLIED).count());
        assertEquals(2, step(report, Stage.SCREENING).count());  // A, plus C who skipped it
        assertEquals(2, step(report, Stage.INTERVIEW).count());
        assertEquals(1, step(report, Stage.OFFER).count());
        assertEquals(0, step(report, Stage.ACCEPTED).count());
    }

    @Test
    void computesPercentagesAndStepToStepConversion() {
        FunnelReport report = scenario();

        assertEquals(50.0, step(report, Stage.SCREENING).percentOfApplied());
        assertEquals(50.0, step(report, Stage.SCREENING).conversionFromPrevious());
        assertEquals(100.0, step(report, Stage.INTERVIEW).conversionFromPrevious());
        assertEquals(50.0, step(report, Stage.OFFER).conversionFromPrevious());
        assertEquals(0.0, step(report, Stage.ACCEPTED).conversionFromPrevious());
        assertNull(step(report, Stage.APPLIED).conversionFromPrevious());
    }

    @Test
    void responseRateCountsRejectionsAsResponses_andNothingElseAsSilence() {
        FunnelReport report = scenario();

        assertEquals(3, report.responded());                      // A, B (rejected), C
        assertEquals(75.0, report.responseRatePercent());
    }

    @Test
    void usesMediansForTimings() {
        FunnelReport report = scenario();

        assertEquals(5.0, report.medianDaysToFirstResponse());               // 3, 5 and 7 days
        assertEquals(5.0, step(report, Stage.SCREENING).medianDaysFromApplied());   // A: 3, C: 7 (via interview)
        assertEquals(8.5, step(report, Stage.INTERVIEW).medianDaysFromApplied());   // A: 10, C: 7
        assertEquals(20.0, step(report, Stage.OFFER).medianDaysFromApplied());
        assertNull(step(report, Stage.ACCEPTED).medianDaysFromApplied());
    }

    @Test
    void recordsWhereRejectionsHappened() {
        FunnelReport report = scenario();

        assertEquals(1, report.rejectedAfter().stream().filter(c -> c.stage().equals("APPLIED")).findFirst().orElseThrow().count());
        assertEquals(0, report.rejectedAfter().stream().filter(c -> c.stage().equals("INTERVIEW")).findFirst().orElseThrow().count());
    }

    @Test
    void groupsApplicationsSentByWeek_zeroFilled() {
        FunnelReport report = scenario();

        assertEquals(3, report.weekly().size());
        assertEquals(LocalDate.of(2026, 11, 2), report.weekly().get(0).weekStart());
        assertEquals(2, report.weekly().get(0).count());          // A and B
        assertEquals(2, report.weekly().get(1).count());          // C and D
        assertEquals(0, report.weekly().get(2).count());          // this week
        assertEquals(LocalDate.of(2026, 11, 16), report.weekly().get(2).weekStart());
    }

    @Test
    void reportsWhereEverythingIsNowSitting() {
        FunnelReport report = scenario();

        Map<String, Integer> now = new java.util.HashMap<>();
        report.currentByStage().forEach(c -> now.put(c.stage(), c.count()));
        assertEquals(1, now.get("OFFER"));      // A
        assertEquals(1, now.get("REJECTED"));   // B
        assertEquals(1, now.get("INTERVIEW"));  // C
        assertEquals(1, now.get("APPLIED"));    // D
        assertEquals(1, now.get("WISHLIST"));   // E
    }

    @Test
    void anArchivedApplicationStillCountsForTheStagesItReached() {
        Application app = create(Stage.APPLIED, 0);
        move(app, Stage.SCREENING, 2);
        move(app, Stage.ARCHIVED, 9);

        FunnelReport report = FunnelCalculator.calculate(applications, events, NOW, 1);

        assertEquals(1, step(report, Stage.SCREENING).count());
        assertEquals(1, report.responded());
    }

    @Test
    void anEmptyAccountGivesZerosAndNullsInsteadOfDividingByZero() {
        FunnelReport report = FunnelCalculator.calculate(List.of(), List.of(), NOW, 4);

        assertEquals(0, report.applied());
        assertNull(report.responseRatePercent());
        assertNull(report.medianDaysToFirstResponse());
        assertEquals(5, report.funnel().size());
        assertNull(report.funnel().get(1).percentOfApplied());
        assertEquals(4, report.weekly().size());
    }

    @Test
    void fallsBackToTheCurrentStageWhenNoHistoryWasRecorded() {
        Application app = new Application(userId, "Acme", "Engineer", null, null, Stage.APPLIED, days(0));
        ReflectionTestUtils.setField(app, "id", UUID.randomUUID());

        FunnelReport report = FunnelCalculator.calculate(List.of(app), List.of(), NOW, 1);

        assertEquals(1, report.applied());
    }
}
