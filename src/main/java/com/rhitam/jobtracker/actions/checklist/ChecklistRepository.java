package com.rhitam.jobtracker.actions.checklist;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChecklistRepository extends JpaRepository<Checklist, UUID> {

    List<Checklist> findByApplicationId(UUID applicationId);
}
