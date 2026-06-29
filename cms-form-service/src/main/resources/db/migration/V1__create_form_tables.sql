CREATE SCHEMA IF NOT EXISTS form;

CREATE TABLE form.form_definitions (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     UUID NOT NULL,  -- logical ref to iamdb.tenants, no FK
    site_id       UUID,           -- logical ref to schemadb.sites, no FK (optional per-site forms)
    code          VARCHAR(100) NOT NULL,
    name          VARCHAR(255) NOT NULL,
    description   TEXT,
    schema        JSONB NOT NULL,       -- JSON Schema for validation
    ui_schema     JSONB NOT NULL,       -- react-jsonschema-form UI schema
    submit_action VARCHAR(100),
    is_active     BOOLEAN NOT NULL DEFAULT true,
    version       INT NOT NULL DEFAULT 1,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(tenant_id, code)
);

CREATE TABLE form.form_submissions (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    form_id        UUID NOT NULL REFERENCES form.form_definitions(id) ON DELETE CASCADE,  -- real FK, same DB
    submitted_data JSONB NOT NULL,
    submitted_by   UUID,   -- logical ref to iamdb.users, no FK (null = anonymous)
    submitted_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ip_address     INET,
    user_agent     TEXT,
    status         VARCHAR(30) NOT NULL DEFAULT 'RECEIVED'
                   CHECK (status IN ('RECEIVED','PROCESSING','PROCESSED','REJECTED'))
);

CREATE INDEX idx_form_submissions_form ON form.form_submissions(form_id, submitted_at DESC);
CREATE INDEX idx_form_submissions_status ON form.form_submissions(status);
