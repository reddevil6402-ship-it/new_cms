-- ============================================================
-- V3: Seed system tenant and SUPER_ADMIN user
--
-- SUPER_ADMIN credentials for local development:
--   email:    admin@cms.system
--   password: Admin@123!
--
-- Password hash below = bcrypt(Admin@123!, cost=12)
-- CHANGE THIS before any non-local deployment.
-- ============================================================

-- System tenant (owns the SUPER_ADMIN account)
INSERT INTO iam.tenants (id, code, name, status, plan)
VALUES (
    'a0000000-0000-7000-8000-000000000001',
    'cms-system',
    'CMS System',
    'ACTIVE',
    'ENTERPRISE'
);

-- SUPER_ADMIN user
-- Password: Admin@123!
-- Hash generated with: bcrypt(cost=12)
INSERT INTO iam.users (id, tenant_id, username, email, password_hash, full_name, status)
VALUES (
    'b0000000-0000-7000-8000-000000000001',
    'a0000000-0000-7000-8000-000000000001',
    'superadmin',
    'admin@cms.system',
    '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LeAXRbQbr5vJfVzba',
    'System Administrator',
    'ACTIVE'
);

-- SUPER_ADMIN role in the system tenant
INSERT INTO iam.roles (id, tenant_id, name, code, description, is_system_role)
VALUES (
    'c0000000-0000-7000-8000-000000000001',
    'a0000000-0000-7000-8000-000000000001',
    'Super Administrator',
    'SUPER_ADMIN',
    'Full system access — manages all tenants and platform configuration',
    true
);

-- Assign ALL permissions to SUPER_ADMIN role
INSERT INTO iam.role_permissions (role_id, permission_id)
SELECT 'c0000000-0000-7000-8000-000000000001', id
FROM iam.permissions;

-- Assign SUPER_ADMIN role to the admin user
INSERT INTO iam.user_roles (user_id, role_id, granted_by)
VALUES (
    'b0000000-0000-7000-8000-000000000001',
    'c0000000-0000-7000-8000-000000000001',
    'b0000000-0000-7000-8000-000000000001'  -- self-granted for seed
);
