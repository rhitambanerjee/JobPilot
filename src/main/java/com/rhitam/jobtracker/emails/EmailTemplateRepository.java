package com.rhitam.jobtracker.emails;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailTemplateRepository extends JpaRepository<EmailTemplate, UUID> {

    /** Built-ins first, then alphabetical. */
    List<EmailTemplate> findAllByOrderByBuiltInDescNameAsc();
}
