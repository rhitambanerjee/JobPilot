package com.rhitam.jobtracker.analytics;

import java.time.LocalDate;
import java.util.List;

/**
 * Everything the Insights page shows. Percentages and day counts are rounded to one decimal; a value is null when
 * there is nothing to compute it from (for example the response rate before the first application).
 */
public record FunnelReport(
        int totalApplications,
        int wishlist,
        int applied,
        int responded,
        Double responseRatePercent,
        Double medianDaysToFirstResponse,
        List<Step> funnel,
        List<StageCount> rejectedAfter,
        List<StageCount> currentByStage,
        List<WeekCount> weekly) {

    /** One step of the funnel: how many applications ever got at least this far. */
    public record Step(String stage, int count, Double percentOfApplied, Double conversionFromPrevious,
                       Double medianDaysFromApplied) {
    }

    public record StageCount(String stage, int count) {
    }

    public record WeekCount(LocalDate weekStart, int count) {
    }
}
