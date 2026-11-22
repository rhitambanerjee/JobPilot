package com.rhitam.jobtracker.emails;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmailTemplateRepository extends JpaRepository<EmailTemplate, UUID> {

    /** What a user sees: the shared built-ins plus their own saved templates. Built-ins first, then A-Z. */
    @Query("""
            select t from EmailTemplate t
            where t.builtIn = true or t.userId = :userId
            order by t.builtIn desc, t.name asc
            """)
    List<EmailTemplate> findVisible(@Param("userId") UUID userId);

    /** One template, only if the user is allowed to see it (built-in, or theirs). */
    @Query("""
            select t from EmailTemplate t
            where t.id = :id and (t.builtIn = true or t.userId = :userId)
            """)
    Optional<EmailTemplate> findVisible(@Param("userId") UUID userId, @Param("id") UUID id);
}
