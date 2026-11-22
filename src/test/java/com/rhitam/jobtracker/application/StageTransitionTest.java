package com.rhitam.jobtracker.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rhitam.jobtracker.common.exception.InvalidTransitionException;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class StageTransitionTest {

    @Test
    void happyPathThroughThePipeline() {
        assertTrue(Stage.APPLIED.canTransitionTo(Stage.SCREENING));
        assertTrue(Stage.SCREENING.canTransitionTo(Stage.INTERVIEW));
        assertTrue(Stage.INTERVIEW.canTransitionTo(Stage.OFFER));
        assertTrue(Stage.OFFER.canTransitionTo(Stage.ACCEPTED));
    }

    @Test
    void cannotSkipBackwardsOrOutOfTerminalStates() {
        assertFalse(Stage.INTERVIEW.canTransitionTo(Stage.APPLIED));
        assertFalse(Stage.REJECTED.canTransitionTo(Stage.INTERVIEW));
        assertFalse(Stage.WISHLIST.canTransitionTo(Stage.OFFER));
    }

    @Test
    void archivedIsFinal() {
        for (Stage next : Stage.values()) {
            assertFalse(Stage.ARCHIVED.canTransitionTo(next));
        }
    }

    @Test
    void everythingExceptArchivedCanBeArchived() {
        for (Stage s : Stage.values()) {
            if (s != Stage.ARCHIVED) {
                assertTrue(s.canTransitionTo(Stage.ARCHIVED), s + " should be archivable");
            }
        }
    }

    @Test
    void entityRejectsIllegalMoveAndKeepsItsStage() {
        Application a = new Application("Rippling", "Backend Engineer", null, null, Stage.APPLIED, Instant.now());
        assertThrows(InvalidTransitionException.class, () -> a.moveTo(Stage.OFFER, Instant.now()));
        assertEquals(Stage.APPLIED, a.getStage());
    }

    @Test
    void legalMoveReturnsPreviousStageAndBumpsActivity() {
        Instant t0 = Instant.parse("2026-01-01T00:00:00Z");
        Instant t1 = t0.plusSeconds(60);
        Application a = new Application("Rippling", "Backend Engineer", null, null, Stage.APPLIED, t0);
        assertEquals(Stage.APPLIED, a.moveTo(Stage.SCREENING, t1));
        assertEquals(t1, a.getLastActivityAt());
    }
}
