-- ============================================================
-- workflowdb migration: V1__create_workflow_tables.sql
-- ============================================================

-- Create schema if not exists
CREATE SCHEMA IF NOT EXISTS workflow;

-- ============================================================
-- WORKFLOW DEFINITIONS (JSONB state machine configs)
-- ============================================================
CREATE TABLE workflow.workflow_definitions (
    id          UUID PRIMARY KEY,
    tenant_id   UUID NOT NULL,
    code        VARCHAR(100) NOT NULL,
    name        VARCHAR(255) NOT NULL,
    description TEXT,
    definition  JSONB NOT NULL,  -- states, transitions, guards, actions
    version     INT NOT NULL DEFAULT 1,
    is_active   BOOLEAN NOT NULL DEFAULT true,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(tenant_id, code, version)
);

-- ============================================================
-- WORKFLOW INSTANCES
-- ============================================================
CREATE TABLE workflow.workflow_instances (
    id                   UUID PRIMARY KEY,
    workflow_def_id      UUID NOT NULL REFERENCES workflow.workflow_definitions(id),
    workflow_def_version INT NOT NULL,
    entity_type          VARCHAR(100) NOT NULL,
    entity_id            UUID NOT NULL,
    current_state        VARCHAR(50) NOT NULL,
    started_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at         TIMESTAMPTZ,
    metadata             JSONB NOT NULL DEFAULT '{}'
);

-- ============================================================
-- WORKFLOW HISTORY (append-only transition log)
-- ============================================================
CREATE TABLE workflow.workflow_history (
    id          UUID PRIMARY KEY,
    instance_id UUID NOT NULL REFERENCES workflow.workflow_instances(id) ON DELETE CASCADE,
    from_state  VARCHAR(50) NOT NULL,
    to_state    VARCHAR(50) NOT NULL,
    trigger     VARCHAR(100) NOT NULL,
    actor_id    UUID,
    actor_email VARCHAR(255),
    comment     TEXT,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    metadata    JSONB NOT NULL DEFAULT '{}'
);

-- ============================================================
-- INDEXES FOR PERFORMANCE AND LOGICAL RETRIEVAL
-- ============================================================
CREATE INDEX idx_workflow_def_tenant ON workflow.workflow_definitions(tenant_id);
CREATE INDEX idx_workflow_entity ON workflow.workflow_instances(entity_type, entity_id);
CREATE INDEX idx_workflow_history_instance ON workflow.workflow_history(instance_id);
CREATE INDEX idx_workflow_history_actor ON workflow.workflow_history(actor_id);
CREATE INDEX idx_workflow_history_time ON workflow.workflow_history(occurred_at DESC);
