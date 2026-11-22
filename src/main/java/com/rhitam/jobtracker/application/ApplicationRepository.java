package com.rhitam.jobtracker.application;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ApplicationRepository extends JpaRepository<Application, UUID> {

    /** The only way user-facing code loads one application: the id AND the owner must match. */
    Optional<Application> findByIdAndUserId(UUID id, UUID userId);

    List<Application> findByUserIdOrderByUpdatedAtDesc(UUID userId);

    List<Application> findByUserIdAndStageOrderByUpdatedAtDesc(UUID userId, Stage stage);

    /**
     * Across ALL users on purpose: this is the daily background scan. Each application's event is
     * published under that application's own owner.
     */
    @Query("""
            select a from Application a
            where a.stage in :stages
              and a.lastActivityAt < :cutoff
            """)
    List<Application> findStale(@Param("stages") Collection<Stage> stages, @Param("cutoff") Instant cutoff);
}
