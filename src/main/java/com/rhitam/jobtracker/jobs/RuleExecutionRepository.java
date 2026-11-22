package com.rhitam.jobtracker.jobs;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RuleExecutionRepository extends JpaRepository<RuleExecution, UUID> {

    /**
     * Due jobs, plus RUNNING jobs whose lock has expired (the worker that took them probably died).
     * SKIP LOCKED lets several workers claim disjoint batches. Call inside a transaction.
     */
    @Query(value = """
            SELECT * FROM rule_executions
            WHERE (status = 'PENDING' AND next_run_at <= :now)
               OR (status = 'RUNNING' AND locked_at < :lockExpiry)
            ORDER BY next_run_at
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<RuleExecution> claim(@Param("now") Instant now,
                              @Param("lockExpiry") Instant lockExpiry,
                              @Param("limit") int limit);

    long countByApplicationId(UUID applicationId);

    long countByStatus(RuleExecution.Status status);
}
