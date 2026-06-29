-- ============================================================
-- V2: Seed system permissions
-- All permission codes used in the CMS permission model.
-- These are system-level — not tenant-scoped.
-- ============================================================

INSERT INTO iam.permissions (resource, action, scope, description) VALUES
  -- Content permissions
  ('content', 'CREATE', 'OWN',  'Create content assigned to self'),
  ('content', 'CREATE', 'ALL',  'Create content for any user'),
  ('content', 'READ',   'OWN',  'Read own content items'),
  ('content', 'READ',   'ALL',  'Read all content items in tenant'),
  ('content', 'UPDATE', 'OWN',  'Edit own content items'),
  ('content', 'UPDATE', 'ALL',  'Edit any content item in tenant'),
  ('content', 'DELETE', 'OWN',  'Delete own content items'),
  ('content', 'DELETE', 'ALL',  'Delete any content item in tenant'),
  ('content', 'PUBLISH','ALL',  'Publish/unpublish any content item'),

  -- Schema permissions
  ('schema',  'CREATE', 'ALL',  'Create content types and field definitions'),
  ('schema',  'READ',   'ALL',  'View content types and field definitions'),
  ('schema',  'UPDATE', 'ALL',  'Modify content types and field definitions'),
  ('schema',  'DELETE', 'ALL',  'Delete content types'),

  -- Workflow permissions
  ('workflow','CREATE', 'ALL',  'Create workflow definitions'),
  ('workflow','READ',   'ALL',  'View workflow definitions and state'),
  ('workflow','UPDATE', 'ALL',  'Modify workflow definitions'),
  ('workflow','EXECUTE','ALL',  'Trigger workflow transitions'),

  -- User management permissions
  ('user',    'CREATE', 'ALL',  'Create users within tenant'),
  ('user',    'READ',   'ALL',  'View users within tenant'),
  ('user',    'UPDATE', 'ALL',  'Edit users within tenant'),
  ('user',    'DELETE', 'ALL',  'Deactivate users within tenant'),

  -- Role management permissions
  ('role',    'CREATE', 'ALL',  'Create roles within tenant'),
  ('role',    'READ',   'ALL',  'View roles within tenant'),
  ('role',    'UPDATE', 'ALL',  'Edit roles and assign permissions'),
  ('role',    'ASSIGN', 'ALL',  'Assign roles to users'),

  -- Media permissions
  ('media',   'UPLOAD', 'OWN',  'Upload media assets'),
  ('media',   'UPLOAD', 'ALL',  'Upload media for any user'),
  ('media',   'READ',   'ALL',  'View media assets'),
  ('media',   'DELETE', 'ALL',  'Delete media assets'),

  -- API key management
  ('apikey',  'CREATE', 'ALL',  'Create API keys for tenant'),
  ('apikey',  'READ',   'ALL',  'View API keys'),
  ('apikey',  'REVOKE', 'ALL',  'Revoke API keys');
