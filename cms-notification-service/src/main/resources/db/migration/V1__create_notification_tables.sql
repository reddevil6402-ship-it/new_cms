CREATE SCHEMA IF NOT EXISTS notification;

CREATE TABLE notification.notification_templates (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     UUID NOT NULL,
    code          VARCHAR(100) NOT NULL,
    name          VARCHAR(255) NOT NULL,
    channel       VARCHAR(20) NOT NULL
                  CHECK (channel IN ('EMAIL','SMS','IN_APP')),
    subject       TEXT,
    body_template TEXT NOT NULL,
    is_active     BOOLEAN NOT NULL DEFAULT true,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(tenant_id, code, channel)
);

CREATE TABLE notification.notification_log (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         UUID NOT NULL,
    notification_type VARCHAR(50) NOT NULL CHECK (notification_type IN ('EMAIL','SMS','IN_APP')),
    recipient         VARCHAR(255) NOT NULL,
    template_code     VARCHAR(100) NOT NULL,
    payload           JSONB NOT NULL,
    status            VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                      CHECK (status IN ('PENDING','SENT','FAILED','RETRYING')),
    sent_at           TIMESTAMPTZ,
    error_message     TEXT,
    retry_count       INT NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_notif_log_status ON notification.notification_log(status);
CREATE INDEX idx_notif_log_tenant ON notification.notification_log(tenant_id, created_at DESC);

CREATE TABLE notification.outbox_events (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type  VARCHAR(100) NOT NULL,
    aggregate_id    UUID NOT NULL,
    event_type      VARCHAR(200) NOT NULL,
    payload         JSONB NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    processed_at    TIMESTAMPTZ,
    retry_count     INT NOT NULL DEFAULT 0,
    error_message   TEXT
);

CREATE INDEX idx_notif_outbox_unprocessed
    ON notification.outbox_events(created_at) WHERE processed_at IS NULL;
