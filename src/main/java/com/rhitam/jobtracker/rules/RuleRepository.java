package com.rhitam.jobtracker.rules;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RuleRepository extends JpaRepository<Rule, UUID> {

    /** Only the event owner's rules: this filter is what stops one user's rule firing on another's data. */
    List<Rule> findByUserIdAndEnabledTrue(UUID userId);

    List<Rule> findByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<Rule> findByIdAndUserId(UUID id, UUID userId);
}
