-- Where follow-up emails go (an HR or careers-page address), saved per application.
ALTER TABLE applications ADD COLUMN contact_email VARCHAR(320);

CREATE TABLE email_templates (
    id         UUID PRIMARY KEY,
    name       VARCHAR(200) NOT NULL,
    subject    VARCHAR(300) NOT NULL,
    body       TEXT         NOT NULL,
    built_in   BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ  NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL
);

-- Emails to third parties (HR). Queued here by the rule's action, delivered later by OutgoingEmailSender.
CREATE TABLE outgoing_emails (
    id             UUID PRIMARY KEY,
    application_id UUID REFERENCES applications (id),
    rule_id        UUID,
    to_address     VARCHAR(320) NOT NULL,
    subject        VARCHAR(300) NOT NULL,
    body           TEXT         NOT NULL,
    status         VARCHAR(20)  NOT NULL,
    attempts       INT          NOT NULL DEFAULT 0,
    last_error     TEXT,
    created_at     TIMESTAMPTZ  NOT NULL,
    sent_at        TIMESTAMPTZ
);
CREATE INDEX idx_outgoing_pending ON outgoing_emails (created_at) WHERE status = 'PENDING';
CREATE INDEX idx_outgoing_application ON outgoing_emails (application_id, created_at DESC);

-- Built-in templates. {{company}}, {{role}}, {{stage}}, {{daysIdle}}, {{appliedDate}}, {{jobUrl}} are filled in
-- automatically; any other {{name}} is a placeholder the user supplies when creating the rule.
INSERT INTO email_templates (id, name, subject, body, built_in, created_at, updated_at) VALUES
(gen_random_uuid(), 'Follow-up after applying',
 $$Following up on my {{role}} application$$,
 $$Hi {{recruiterName}},

I applied for the {{role}} position at {{company}} on {{appliedDate}} and wanted to check on the status of my application. I'm still very interested in the role and happy to share anything that would help.

Thank you for your time.

Best regards,
{{yourName}}$$,
 TRUE, now(), now()),
(gen_random_uuid(), 'Polite status check',
 $$Checking in: {{role}} at {{company}}$$,
 $$Hello,

It has been {{daysIdle}} days since I applied for the {{role}} role at {{company}}. Could you let me know if there is an update, or if you need anything further from me?

Thanks,
{{yourName}}$$,
 TRUE, now(), now()),
(gen_random_uuid(), 'Request for an update (careers team)',
 $$Request for an update: {{role}} application$$,
 $$Hello {{company}} Careers Team,

I submitted an application for the {{role}} position on {{appliedDate}} and would appreciate any update on its status. Please let me know if you need anything else from me.

Kind regards,
{{yourName}}$$,
 TRUE, now(), now());
