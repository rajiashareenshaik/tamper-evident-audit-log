CREATE TABLE audit_event_archive (
    event_id UUID PRIMARY KEY REFERENCES audit_event(event_id),
    archived_at TIMESTAMPTZ NOT NULL,
    policy_cutoff TIMESTAMPTZ NOT NULL
);

CREATE TABLE audit_redaction_value (
    redaction_id UUID PRIMARY KEY,
    event_id UUID NOT NULL REFERENCES audit_event(event_id),
    field_path VARCHAR(1000) NOT NULL,
    encrypted_value TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    redacted_at TIMESTAMPTZ,
    UNIQUE (event_id, field_path)
);

CREATE INDEX idx_audit_archive_cutoff ON audit_event_archive(policy_cutoff);
CREATE INDEX idx_audit_redaction_event ON audit_redaction_value(event_id);
