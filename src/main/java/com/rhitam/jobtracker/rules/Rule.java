package com.rhitam.jobtracker.rules;

import com.rhitam.jobtracker.rules.model.ActionSpec;
import com.rhitam.jobtracker.rules.model.Condition;
import com.rhitam.jobtracker.rules.model.Trigger;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** "When <trigger> and <all conditions> then run <actions>". Trigger/conditions/actions are JSONB. */
@Entity
@Table(name = "rules")
public class Rule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String name;
    private boolean enabled;

    @Column(name = "trigger_spec")
    @JdbcTypeCode(SqlTypes.JSON)
    private Trigger trigger;

    @JdbcTypeCode(SqlTypes.JSON)
    private List<Condition> conditions;

    @JdbcTypeCode(SqlTypes.JSON)
    private List<ActionSpec> actions;

    private Instant createdAt;

    protected Rule() {
    }

    public Rule(String name, Trigger trigger, List<Condition> conditions, List<ActionSpec> actions,
                boolean enabled, Instant now) {
        this.name = name;
        this.trigger = trigger;
        this.conditions = conditions;
        this.actions = actions;
        this.enabled = enabled;
        this.createdAt = now;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public boolean isEnabled() { return enabled; }
    public Trigger getTrigger() { return trigger; }
    public List<Condition> getConditions() { return conditions; }
    public List<ActionSpec> getActions() { return actions; }
    public Instant getCreatedAt() { return createdAt; }
}
