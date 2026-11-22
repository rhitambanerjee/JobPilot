package com.rhitam.jobtracker.application;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ApplicationRepository extends JpaRepository<Application, UUID> {

    List<Application> findAllByOrderByUpdatedAtDesc();

    List<Application> findByStageOrderByUpdatedAtDesc(Stage stage);

    /** Applications in the given stages with no activity since the cutoff. Events are emitted daily; rules dedupe. */
    @Query("""
            select a from Application a
            where a.stage in :stages
              and a.lastActivityAt < :cutoff
            """)
    List<Application> findStale(@Param("stages") Collection<Stage> stages, @Param("cutoff") Instant cutoff);
}
