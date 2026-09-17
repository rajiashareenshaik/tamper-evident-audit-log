CREATE TABLE audit_event (
    sequence_id BIGSERIAL PRIMARY KEY,
    event_id UUID NOT NULL UNIQUE,
    event_type VARCHAR(100) NOT NULL,
    actor_id VARCHAR(200) NOT NULL,
    resource_type VARCHAR(100) NOT NULL,
    resource_id VARCHAR(200) NOT NULL,
    integrity_payload JSONB NOT NULL,
    event_timestamp TIMESTAMPTZ NOT NULL,
    schema_version INTEGER NOT NULL DEFAULT 1,
    content_hash VARCHAR(64) NOT NULL,
    previous_hash VARCHAR(64) NOT NULL,
    chain_hash VARCHAR(64) NOT NULL UNIQUE,
    idempotency_key VARCHAR(200),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE audit_chain_head (
    chain_id VARCHAR(50) PRIMARY KEY,
    last_sequence_id BIGINT,
    last_event_id UUID,
    last_chain_hash VARCHAR(64) NOT NULL,
    event_count BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_audit_actor
ON audit_event(actor_id);

CREATE INDEX idx_audit_resource
ON audit_event(resource_type, resource_id);

CREATE INDEX idx_audit_event_type
ON audit_event(event_type);

CREATE INDEX idx_audit_timestamp
ON audit_event(event_timestamp);

CREATE INDEX idx_audit_actor_timestamp
ON audit_event(actor_id, event_timestamp);

CREATE INDEX idx_audit_resource_timestamp
ON audit_event(resource_id, event_timestamp);
