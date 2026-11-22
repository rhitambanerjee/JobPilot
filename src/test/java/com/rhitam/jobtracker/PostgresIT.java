package com.rhitam.jobtracker;

import com.rhitam.jobtracker.auth.User;
import com.rhitam.jobtracker.auth.UserRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base class for integration tests. One Postgres container is shared by every IT class (started once per
 * JVM), schedulers are off so tests call the pollers explicitly, and tables are wiped before each test.
 */
@SpringBootTest(properties = "tracker.scheduling.enabled=false")
public abstract class PostgresIT {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected UserRepository users;

    @BeforeEach
    void cleanDatabase() {
        jdbc.execute("TRUNCATE applications, audit_logs, outbox_events, rules, rule_executions, "
                + "checklists, reminders, notifications, notification_settings, outgoing_emails CASCADE");
        jdbc.update("DELETE FROM email_templates WHERE built_in = FALSE");   // keep the built-in ones
        jdbc.update("DELETE FROM users");                                    // incl. the migration's placeholder owner
    }

    /** A fresh account; returns its id. */
    protected UUID newUser() {
        return users.save(new User("user-" + UUID.randomUUID() + "@example.com", "not-a-real-hash", null, Instant.now()))
                .getId();
    }
}
