CREATE SCHEMA IF NOT EXISTS audit;

CREATE TABLE audit.audit_events (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type      VARCHAR(100) NOT NULL,
    actor_id        UUID,
    actor_email     VARCHAR(255),
    entity_type     VARCHAR(100),
    entity_id       UUID,
    tenant_id       UUID,
    source_service  VARCHAR(100),
    old_value       JSONB,
    new_value       JSONB,
    ip_address      INET,
    user_agent      TEXT,
    occurred_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    correlation_id  UUID
);

CREATE INDEX idx_audit_entity ON audit.audit_events(entity_type, entity_id);
CREATE INDEX idx_audit_actor ON audit.audit_events(actor_id);
CREATE INDEX idx_audit_type ON audit.audit_events(event_type);
CREATE INDEX idx_audit_tenant ON audit.audit_events(tenant_id);
CREATE INDEX idx_audit_time ON audit.audit_events(occurred_at DESC);
