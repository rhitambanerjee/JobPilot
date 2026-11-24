package com.rhitam.jobtracker.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rhitam.jobtracker.PostgresIT;
import com.rhitam.jobtracker.application.dto.CreateApplicationRequest;
import com.rhitam.jobtracker.application.dto.UpdateApplicationRequest;
import com.rhitam.jobtracker.audit.AuditLog;
import com.rhitam.jobtracker.audit.AuditRepository;
import com.rhitam.jobtracker.common.exception.BadRequestException;
import com.rhitam.jobtracker.common.exception.NotFoundException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ApplicationEditIT extends PostgresIT {

    @Autowired ApplicationService applications;
    @Autowired AuditRepository audit;

    private Application create(UUID userId) {
        return applications.create(userId, new CreateApplicationRequest(
                "Acme", "Engineer", "https://acme.example/jobs/1", "first note", null, null));
    }

    @Test
    void editsTheDetails_keepsStageAndIdleClock_andWritesOneAuditRow() {
        UUID userId = newUser();
        Application created = create(userId);
        Application before = applications.get(userId, created.getId());

        Application updated = applications.updateDetails(userId, created.getId(), new UpdateApplicationRequest(
                "  Acme Corp ", "Senior Engineer", "https://acme.example/jobs/2", "new note"));

        assertEquals("Acme Corp", updated.getCompany());                     // trimmed
        assertEquals("Senior Engineer", updated.getRole());
        assertEquals("https://acme.example/jobs/2", updated.getJobUrl());
        assertEquals("new note", updated.getNotes());
        assertEquals(Stage.APPLIED, updated.getStage());
        assertEquals(before.getLastActivityAt(), applications.get(userId, created.getId()).getLastActivityAt());

        List<AuditLog> history = audit.findByEntityIdAndUserIdOrderByCreatedAtDesc(created.getId(), userId);
        assertEquals("DETAILS_UPDATED", history.get(0).getAction());
        assertTrue(history.get(0).getDetails().containsKey("company"));
        assertTrue(history.get(0).getDetails().containsKey("notes"));
    }

    @Test
    void savingWithoutChangesWritesNoAuditRow() {
        UUID userId = newUser();
        Application created = create(userId);
        long rowsBefore = audit.count();

        applications.updateDetails(userId, created.getId(), new UpdateApplicationRequest(
                "Acme", "Engineer", "https://acme.example/jobs/1", "first note"));

        assertEquals(rowsBefore, audit.count());
    }

    @Test
    void blankLinkAndNotesAreClearedRatherThanStoredAsEmptyStrings() {
        UUID userId = newUser();
        Application created = create(userId);

        Application updated = applications.updateDetails(userId, created.getId(),
                new UpdateApplicationRequest("Acme", "Engineer", "   ", ""));

        assertNull(updated.getJobUrl());
        assertNull(updated.getNotes());
    }

    @Test
    void onlyWebLinksAreAccepted() {
        UUID userId = newUser();
        Application created = create(userId);

        assertThrows(BadRequestException.class, () -> applications.updateDetails(userId, created.getId(),
                new UpdateApplicationRequest("Acme", "Engineer", "javascript:alert(1)", null)));
        assertThrows(BadRequestException.class, () -> applications.create(userId,
                new CreateApplicationRequest("Acme", "Engineer", "ftp://acme.example", null, null, null)));
    }

    @Test
    void anotherUserCannotEditIt() {
        UUID owner = newUser();
        UUID other = newUser();
        Application created = create(owner);

        assertThrows(NotFoundException.class, () -> applications.updateDetails(other, created.getId(),
                new UpdateApplicationRequest("Hacked", "Hacked", null, null)));
        assertEquals("Acme", applications.get(owner, created.getId()).getCompany());
    }
}
