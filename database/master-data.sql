/*
  Master data (lookups, RBAC catalogue, SLA, workflow seeds, calendars).
  No passwords, connection strings, or LDAP secrets.
  SQL Server 2016 / 2019. Re-runnable: deletes seed rows that we control by code.
*/
SET NOCOUNT ON;
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
GO

USE [ItsmPortal];
GO

/* ---- departments ---- */
MERGE dbo.department AS t
USING (VALUES
    (N'IT', N'Information Technology'),
    (N'FIN', N'Finance'),
    (N'HR', N'Human Resources'),
    (N'OPS', N'Operations'),
    (N'CRD', N'Credit'),
    (N'COL', N'Collections'),
    (N'LGL', N'Legal'),
    (N'RSK', N'Risk'),
    (N'CMP', N'Compliance'),
    (N'ADM', N'Administration')
) AS s(code, name)
ON t.code = s.code
WHEN NOT MATCHED THEN INSERT (code, name) VALUES (s.code, s.name);
GO

MERGE dbo.role AS t
USING (VALUES
    (N'EMPLOYEE', N'Employee'),
    (N'MANAGER', N'Manager'),
    (N'HOD', N'HOD'),
    (N'CISO', N'CISO'),
    (N'IT_SERVICE_DESK', N'IT Service Desk'),
    (N'IT_IMPLEMENTOR', N'IT Implementor'),
    (N'IT_ADMIN', N'IT Admin'),
    (N'SYSTEM_ADMINISTRATOR', N'System Administrator')
) AS s(code, name)
ON t.code = s.code
WHEN NOT MATCHED THEN INSERT (code, name, is_system, is_active) VALUES (s.code, s.name, 1, 1);
GO

MERGE dbo.permission AS t
USING (VALUES
    (N'TICKET_CREATE', N'Create tickets'),
    (N'TICKET_VIEW_OWN', N'View own tickets'),
    (N'TICKET_VIEW_TEAM', N'View team tickets'),
    (N'TICKET_VIEW_DEPARTMENT', N'View department tickets'),
    (N'TICKET_VIEW_SECURITY', N'View security tickets'),
    (N'TICKET_VIEW_QUEUE_ALL', N'View service desk queue'),
    (N'TICKET_APPROVE_ASSIGNED_STAGE', N'Act on assigned workflow stage'),
    (N'TICKET_ASSIGN', N'Assign implementor'),
    (N'TICKET_FULFIL', N'Fulfil / resolve'),
    (N'SLA_MONITOR', N'Monitor SLA'),
    (N'REPORT_VIEW', N'View reports'),
    (N'KB_READ', N'Read knowledge base'),
    (N'AUDIT_VIEW', N'View audit log'),
    (N'ADMIN_USER_MANAGE', N'Manage portal users and roles'),
    (N'ADMIN_MASTERDATA_PROPOSE', N'Propose master-data changes'),
    (N'ADMIN_MASTERDATA_APPROVE', N'Approve master-data changes (checker)'),
    (N'ADMIN_SYSTEM', N'System settings (non-secret)'),
    (N'ASSET_MANAGE', N'Manage assets')
) AS s(code, description)
ON t.code = s.code
WHEN NOT MATCHED THEN INSERT (code, description) VALUES (s.code, s.description);
GO

/* Role-permission map: delete and re-seed system roles only */
DELETE rp
FROM dbo.role_permission rp
INNER JOIN dbo.role r ON r.role_id = rp.role_id
WHERE r.is_system = 1;

INSERT INTO dbo.role_permission (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM dbo.role r
CROSS JOIN dbo.permission p
WHERE r.code = N'EMPLOYEE'
  AND p.code IN (N'TICKET_CREATE', N'TICKET_VIEW_OWN', N'TICKET_APPROVE_ASSIGNED_STAGE', N'KB_READ');

INSERT INTO dbo.role_permission (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM dbo.role r
CROSS JOIN dbo.permission p
WHERE r.code = N'MANAGER'
  AND p.code IN (N'TICKET_CREATE', N'TICKET_VIEW_OWN', N'TICKET_VIEW_TEAM', N'TICKET_APPROVE_ASSIGNED_STAGE', N'REPORT_VIEW', N'KB_READ');

INSERT INTO dbo.role_permission (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM dbo.role r
CROSS JOIN dbo.permission p
WHERE r.code = N'HOD'
  AND p.code IN (N'TICKET_CREATE', N'TICKET_VIEW_OWN', N'TICKET_VIEW_DEPARTMENT', N'TICKET_APPROVE_ASSIGNED_STAGE', N'REPORT_VIEW', N'KB_READ');

INSERT INTO dbo.role_permission (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM dbo.role r
CROSS JOIN dbo.permission p
WHERE r.code = N'CISO'
  AND p.code IN (N'TICKET_CREATE', N'TICKET_VIEW_OWN', N'TICKET_VIEW_SECURITY', N'TICKET_APPROVE_ASSIGNED_STAGE', N'REPORT_VIEW', N'AUDIT_VIEW');

INSERT INTO dbo.role_permission (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM dbo.role r
CROSS JOIN dbo.permission p
WHERE r.code = N'IT_SERVICE_DESK'
  AND p.code IN (N'TICKET_CREATE', N'TICKET_VIEW_OWN', N'TICKET_VIEW_QUEUE_ALL', N'TICKET_ASSIGN', N'SLA_MONITOR', N'REPORT_VIEW', N'KB_READ');

INSERT INTO dbo.role_permission (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM dbo.role r
CROSS JOIN dbo.permission p
WHERE r.code = N'IT_IMPLEMENTOR'
  AND p.code IN (N'TICKET_CREATE', N'TICKET_VIEW_OWN', N'TICKET_FULFIL', N'KB_READ');

INSERT INTO dbo.role_permission (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM dbo.role r
CROSS JOIN dbo.permission p
WHERE r.code = N'IT_ADMIN'
  AND p.code IN (
    N'TICKET_CREATE', N'TICKET_VIEW_OWN', N'TICKET_VIEW_QUEUE_ALL', N'REPORT_VIEW',
    N'AUDIT_VIEW', N'ADMIN_USER_MANAGE', N'ADMIN_MASTERDATA_PROPOSE', N'ADMIN_MASTERDATA_APPROVE', N'ASSET_MANAGE');

INSERT INTO dbo.role_permission (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM dbo.role r
CROSS JOIN dbo.permission p
WHERE r.code = N'SYSTEM_ADMINISTRATOR';
GO

MERGE dbo.ticket_type AS t
USING (VALUES
    (N'INCIDENT', N'Incident', 10),
    (N'SERVICE_REQUEST', N'Service Request', 20),
    (N'ACCESS_REQUEST', N'Access Request', 30),
    (N'CHANGE_REQUEST', N'Change Request', 40),
    (N'SECURITY_INCIDENT', N'Security Incident', 50),
    (N'PROBLEM', N'Problem', 60),
    (N'HARDWARE_REQUEST', N'Hardware Request', 70),
    (N'SOFTWARE_REQUEST', N'Software Request', 80)
) AS s(code, name, sort_order)
ON t.code = s.code
WHEN NOT MATCHED THEN INSERT (code, name, sort_order) VALUES (s.code, s.name, s.sort_order);
GO

MERGE dbo.category AS t
USING (VALUES
    (N'HARDWARE', N'Hardware', 10),
    (N'SOFTWARE', N'Software', 20),
    (N'NETWORK', N'Network', 30),
    (N'EMAIL', N'Email', 40),
    (N'APPLICATION', N'Application', 50),
    (N'DATABASE', N'Database', 60),
    (N'ACCESS_MGMT', N'Access Management', 70),
    (N'CYBER_SECURITY', N'Cyber Security', 80),
    (N'INFRASTRUCTURE', N'Infrastructure', 90),
    (N'CLOUD', N'Cloud', 100),
    (N'TELEPHONY', N'Telephony', 110),
    (N'OTHER', N'Other', 120)
) AS s(code, name, sort_order)
ON t.code = s.code
WHEN NOT MATCHED THEN INSERT (code, name, sort_order) VALUES (s.code, s.name, s.sort_order);
GO

;WITH subs AS (
    SELECT c.category_id, v.code, v.name, v.sort_order
    FROM dbo.category c
    INNER JOIN (VALUES
        (N'HARDWARE', N'LAPTOP', N'Laptop Issue', 10),
        (N'HARDWARE', N'DESKTOP', N'Desktop Issue', 20),
        (N'HARDWARE', N'PRINTER', N'Printer Issue', 30),
        (N'HARDWARE', N'PERIPHERAL', N'Peripheral Request', 40),
        (N'HARDWARE', N'REPLACEMENT', N'Hardware Replacement', 50),
        (N'SOFTWARE', N'INSTALL', N'Software Installation', 10),
        (N'SOFTWARE', N'LICENSE', N'Software License', 20),
        (N'SOFTWARE', N'BUG', N'Software Bug', 30),
        (N'SOFTWARE', N'UPGRADE', N'Upgrade Request', 40),
        (N'NETWORK', N'VPN', N'VPN Access', 10),
        (N'NETWORK', N'WIFI', N'Wi-Fi Connectivity', 20),
        (N'NETWORK', N'LAN', N'LAN Connectivity', 30),
        (N'NETWORK', N'BW', N'Bandwidth Issue', 40),
        (N'EMAIL', N'MAILBOX', N'Mailbox Issue', 10),
        (N'EMAIL', N'DL', N'Distribution List', 20),
        (N'EMAIL', N'ACCESS', N'Email Access', 30),
        (N'EMAIL', N'PHISH', N'Spam/Phishing Report', 40),
        (N'APPLICATION', N'ERP', N'ERP Issue', 10),
        (N'APPLICATION', N'CRM', N'CRM Issue', 20),
        (N'APPLICATION', N'CBA', N'Core Banking Application', 30),
        (N'APPLICATION', N'PORTAL', N'Internal Portal Issue', 40),
        (N'DATABASE', N'DATA_ACCESS', N'Data Access Request', 10),
        (N'DATABASE', N'QUERY', N'Query Support', 20),
        (N'DATABASE', N'PERF', N'Database Performance', 30),
        (N'DATABASE', N'BACKUP', N'Backup/Restore', 40),
        (N'ACCESS_MGMT', N'NEW_USER', N'New User Access', 10),
        (N'ACCESS_MGMT', N'MODIFY', N'Access Modification', 20),
        (N'ACCESS_MGMT', N'REVOKE', N'Access Revocation', 30),
        (N'ACCESS_MGMT', N'PRIVILEGED', N'Privileged Access Request', 40),
        (N'ACCESS_MGMT', N'FILE_FOLDER', N'File/Folder Access Request', 50),
        (N'ACCESS_MGMT', N'NSHARE', N'Network Share Access', 60),
        (N'CYBER_SECURITY', N'PHISHING', N'Phishing Incident', 10),
        (N'CYBER_SECURITY', N'MALWARE', N'Malware/Virus', 20),
        (N'CYBER_SECURITY', N'LEAK', N'Data Leak Concern', 30),
        (N'CYBER_SECURITY', N'EXCEPTION', N'Security Policy Exception', 40),
        (N'INFRASTRUCTURE', N'SERVER', N'Server Issue', 10),
        (N'INFRASTRUCTURE', N'DC', N'Data Center Request', 20),
        (N'INFRASTRUCTURE', N'CLOUD_RES', N'Cloud Resource', 30),
        (N'INFRASTRUCTURE', N'UPS', N'UPS/Power', 40),
        (N'CLOUD', N'STORAGE', N'Cloud Storage', 10),
        (N'CLOUD', N'VM', N'Cloud VM Provisioning', 20),
        (N'CLOUD', N'SAAS', N'SaaS Subscription', 30),
        (N'CLOUD', N'ACCESS', N'Cloud Access', 40),
        (N'TELEPHONY', N'EXT', N'Extension Setup', 10),
        (N'TELEPHONY', N'BRIDGE', N'Conference Bridge', 20),
        (N'TELEPHONY', N'SIM', N'Mobile SIM/Device', 30),
        (N'OTHER', N'GENERAL', N'General Query', 10),
        (N'OTHER', N'MISC', N'Miscellaneous', 20)
    ) AS v(cat_code, code, name, sort_order) ON v.cat_code = c.code
)
MERGE dbo.sub_category AS t
USING subs AS s
ON t.category_id = s.category_id AND t.code = s.code
WHEN NOT MATCHED THEN INSERT (category_id, code, name, sort_order) VALUES (s.category_id, s.code, s.name, s.sort_order);
GO

MERGE dbo.sla_policy AS t
USING (VALUES
    (N'Critical', 15, 240, 0),
    (N'High', 30, 480, 0),
    (N'Medium', 120, 1440, 0),
    (N'Low', 240, 4320, 0)
) AS s(priority_code, response_minutes, resolution_minutes, is_24x7)
ON t.priority_code = s.priority_code
WHEN MATCHED THEN UPDATE SET response_minutes = s.response_minutes, resolution_minutes = s.resolution_minutes
WHEN NOT MATCHED THEN INSERT (priority_code, response_minutes, resolution_minutes, is_24x7)
     VALUES (s.priority_code, s.response_minutes, s.resolution_minutes, s.is_24x7);
GO

IF NOT EXISTS (SELECT 1 FROM dbo.ticket_number_config WHERE sequence_year = 2026)
    INSERT INTO dbo.ticket_number_config (prefix, include_year, padding, sequence_year, last_allocated)
    VALUES (N'ITSM', 1, 6, 2026, 1200);
GO

IF NOT EXISTS (SELECT 1 FROM dbo.attachment_policy)
    INSERT INTO dbo.attachment_policy (max_bytes, allowed_extensions, allowed_mime_types, virus_scan_required)
    VALUES (
        10485760,
        N'.pdf,.doc,.docx,.xls,.xlsx,.png,.jpg,.jpeg,.txt,.log,.msg',
        N'application/pdf,application/msword,application/vnd.openxmlformats-officedocument.wordprocessingml.document,image/png,image/jpeg,text/plain',
        0
    );
GO

MERGE dbo.business_calendar AS t
USING (VALUES
    (1, CAST(N'09:00:00' AS TIME(0)), CAST(N'18:00:00' AS TIME(0)), 1),
    (2, CAST(N'09:00:00' AS TIME(0)), CAST(N'18:00:00' AS TIME(0)), 1),
    (3, CAST(N'09:00:00' AS TIME(0)), CAST(N'18:00:00' AS TIME(0)), 1),
    (4, CAST(N'09:00:00' AS TIME(0)), CAST(N'18:00:00' AS TIME(0)), 1),
    (5, CAST(N'09:00:00' AS TIME(0)), CAST(N'18:00:00' AS TIME(0)), 1),
    (6, CAST(N'00:00:00' AS TIME(0)), CAST(N'00:00:00' AS TIME(0)), 0),
    (7, CAST(N'00:00:00' AS TIME(0)), CAST(N'00:00:00' AS TIME(0)), 0)
) AS s(weekday_iso, start_time, end_time, is_working_day)
ON t.weekday_iso = s.weekday_iso
WHEN MATCHED THEN UPDATE SET start_time = s.start_time, end_time = s.end_time, is_working_day = s.is_working_day
WHEN NOT MATCHED THEN INSERT (weekday_iso, start_time, end_time, timezone_id, is_working_day)
     VALUES (s.weekday_iso, s.start_time, s.end_time, N'India Standard Time', s.is_working_day);
GO

MERGE dbo.holiday AS t
USING (VALUES
    (CAST(N'2026-01-26' AS DATE), N'Republic Day'),
    (CAST(N'2026-08-15' AS DATE), N'Independence Day'),
    (CAST(N'2026-10-02' AS DATE), N'Gandhi Jayanti')
) AS s(holiday_date, name)
ON t.holiday_date = s.holiday_date
WHEN NOT MATCHED THEN INSERT (holiday_date, name, is_national) VALUES (s.holiday_date, s.name, 1);
GO

MERGE dbo.assignment_group AS t
USING (VALUES
    (N'IT_SERVICE_DESK', N'IT Service Desk'),
    (N'IT_IMPLEMENTORS', N'IT Implementors'),
    (N'IT_SECURITY_TEAM', N'IT Security Team')
) AS s(code, name)
ON t.code = s.code
WHEN NOT MATCHED THEN INSERT (code, name) VALUES (s.code, s.name);
GO

/* ---- Workflow definitions (seed templates; engine-driven, not Java if-else) ---- */
MERGE dbo.workflow_definition AS t
USING (VALUES
    (N'SR_CHAIN_TO_HOD_CISO_IMPL', N'Service Request — chain to HOD, CISO, Implementor', 1, N'Active',
        N'Default SR: expand LDAP manager hops until HOD, then CISO, then Implementor.'),
    (N'SR_CHAIN_TO_HOD_IMPL', N'Service Request — chain to HOD, Implementor (no CISO)', 1, N'Active',
        N'Optional SR clone without CISO. Bind via a rule on non-security categories.'),
    (N'INCIDENT_SD_THEN_IMPL', N'Incident — Service Desk then Implementor', 1, N'Active',
        N'Default Incident: SD triage/assign, then Implementor, requester confirmation, close.'),
    (N'INCIDENT_DIRECT_IMPL', N'Incident — direct to Implementor', 1, N'Inactive',
        N'Optional skip-SD template. Inactive until an admin activates a higher-priority rule.'),
    (N'SECURITY', N'Security / highly confidential', 1, N'Active',
        N'Manager chain to HOD, CISO, Implementor (same shape as default SR).'),
    (N'PRIVILEGED_ACCESS', N'Privileged access', 1, N'Active',
        N'Chain to HOD, CISO, IT Security Team, Implementor.')
) AS s(code, name, version_no, status_code, description)
ON t.code = s.code AND t.version_no = s.version_no
WHEN MATCHED THEN UPDATE SET name = s.name, status_code = s.status_code, description = s.description
WHEN NOT MATCHED THEN INSERT (code, name, version_no, status_code, description)
     VALUES (s.code, s.name, s.version_no, s.status_code, s.description);
GO

/* Replace stages/transitions/rules for seed codes (safe on empty or re-seed) */
DELETE wst
FROM dbo.workflow_stage_transition wst
INNER JOIN dbo.workflow_stage ws ON ws.workflow_stage_id = wst.workflow_stage_id
INNER JOIN dbo.workflow_definition wd ON wd.workflow_definition_id = ws.workflow_definition_id
WHERE wd.code IN (N'SR_CHAIN_TO_HOD_CISO_IMPL', N'SR_CHAIN_TO_HOD_IMPL', N'INCIDENT_SD_THEN_IMPL',
                  N'INCIDENT_DIRECT_IMPL', N'SECURITY', N'PRIVILEGED_ACCESS');

DELETE wr
FROM dbo.workflow_rule wr
INNER JOIN dbo.workflow_definition wd ON wd.workflow_definition_id = wr.workflow_definition_id
WHERE wd.code IN (N'SR_CHAIN_TO_HOD_CISO_IMPL', N'SR_CHAIN_TO_HOD_IMPL', N'INCIDENT_SD_THEN_IMPL',
                  N'INCIDENT_DIRECT_IMPL', N'SECURITY', N'PRIVILEGED_ACCESS')
  AND NOT EXISTS (SELECT 1 FROM dbo.workflow_instance wi WHERE wi.workflow_rule_id = wr.workflow_rule_id);

DELETE ws
FROM dbo.workflow_stage ws
INNER JOIN dbo.workflow_definition wd ON wd.workflow_definition_id = ws.workflow_definition_id
WHERE wd.code IN (N'SR_CHAIN_TO_HOD_CISO_IMPL', N'SR_CHAIN_TO_HOD_IMPL', N'INCIDENT_SD_THEN_IMPL',
                  N'INCIDENT_DIRECT_IMPL', N'SECURITY', N'PRIVILEGED_ACCESS')
  AND NOT EXISTS (
        SELECT 1 FROM dbo.workflow_instance_stage wis WHERE wis.workflow_stage_id = ws.workflow_stage_id);
GO

DECLARE @ciso BIGINT = (SELECT role_id FROM dbo.role WHERE code = N'CISO');
DECLARE @secGrp BIGINT = (SELECT assignment_group_id FROM dbo.assignment_group WHERE code = N'IT_SECURITY_TEAM');
DECLARE @sdGrp BIGINT = (SELECT assignment_group_id FROM dbo.assignment_group WHERE code = N'IT_SERVICE_DESK');
DECLARE @implGrp BIGINT = (SELECT assignment_group_id FROM dbo.assignment_group WHERE code = N'IT_IMPLEMENTORS');

DECLARE @sr BIGINT = (SELECT workflow_definition_id FROM dbo.workflow_definition WHERE code = N'SR_CHAIN_TO_HOD_CISO_IMPL' AND version_no = 1);
DECLARE @srNoCiso BIGINT = (SELECT workflow_definition_id FROM dbo.workflow_definition WHERE code = N'SR_CHAIN_TO_HOD_IMPL' AND version_no = 1);
DECLARE @inc BIGINT = (SELECT workflow_definition_id FROM dbo.workflow_definition WHERE code = N'INCIDENT_SD_THEN_IMPL' AND version_no = 1);
DECLARE @incDirect BIGINT = (SELECT workflow_definition_id FROM dbo.workflow_definition WHERE code = N'INCIDENT_DIRECT_IMPL' AND version_no = 1);
DECLARE @sec BIGINT = (SELECT workflow_definition_id FROM dbo.workflow_definition WHERE code = N'SECURITY' AND version_no = 1);
DECLARE @priv BIGINT = (SELECT workflow_definition_id FROM dbo.workflow_definition WHERE code = N'PRIVILEGED_ACCESS' AND version_no = 1);

IF NOT EXISTS (SELECT 1 FROM dbo.workflow_stage WHERE workflow_definition_id = @sr)
BEGIN
    INSERT INTO dbo.workflow_stage (workflow_definition_id, stage_order, code, label, stage_type, actor_strategy, role_id, assignment_group_id, send_back_target)
    VALUES
        (@sr, 10, N'hierarchy', N'Reporting hierarchy through HOD', N'APPROVAL', N'DYNAMIC_HIERARCHY_TO_HOD', NULL, NULL, N'PREVIOUS_STAGE'),
        (@sr, 20, N'ciso', N'CISO approval', N'APPROVAL', N'NAMED_ROLE', @ciso, NULL, N'PREVIOUS_STAGE'),
        (@sr, 30, N'implementor', N'Implementor', N'FULFILMENT', N'IMPLEMENTOR', NULL, @implGrp, NULL),
        (@sr, 40, N'confirmation', N'Requester confirmation', N'CONFIRMATION', N'REQUESTER', NULL, NULL, NULL),
        (@sr, 50, N'closed', N'Closed', N'CLOSURE', N'SYSTEM', NULL, NULL, NULL);
END

IF NOT EXISTS (SELECT 1 FROM dbo.workflow_stage WHERE workflow_definition_id = @srNoCiso)
BEGIN
    INSERT INTO dbo.workflow_stage (workflow_definition_id, stage_order, code, label, stage_type, actor_strategy, role_id, assignment_group_id, send_back_target)
    VALUES
        (@srNoCiso, 10, N'hierarchy', N'Reporting hierarchy through HOD', N'APPROVAL', N'DYNAMIC_HIERARCHY_TO_HOD', NULL, NULL, N'PREVIOUS_STAGE'),
        (@srNoCiso, 20, N'implementor', N'Implementor', N'FULFILMENT', N'IMPLEMENTOR', NULL, @implGrp, NULL),
        (@srNoCiso, 30, N'confirmation', N'Requester confirmation', N'CONFIRMATION', N'REQUESTER', NULL, NULL, NULL),
        (@srNoCiso, 40, N'closed', N'Closed', N'CLOSURE', N'SYSTEM', NULL, NULL, NULL);
END

IF NOT EXISTS (SELECT 1 FROM dbo.workflow_stage WHERE workflow_definition_id = @inc)
BEGIN
    INSERT INTO dbo.workflow_stage (workflow_definition_id, stage_order, code, label, stage_type, actor_strategy, role_id, assignment_group_id, send_back_target)
    VALUES
        (@inc, 10, N'servicedesk', N'IT Service Desk triage', N'ASSIGNMENT', N'SERVICE_DESK', NULL, @sdGrp, NULL),
        (@inc, 20, N'implementor', N'Implementor', N'FULFILMENT', N'IMPLEMENTOR', NULL, @implGrp, NULL),
        (@inc, 30, N'confirmation', N'Requester confirmation', N'CONFIRMATION', N'REQUESTER', NULL, NULL, NULL),
        (@inc, 40, N'closed', N'Closed', N'CLOSURE', N'SYSTEM', NULL, NULL, NULL);
END

IF NOT EXISTS (SELECT 1 FROM dbo.workflow_stage WHERE workflow_definition_id = @incDirect)
BEGIN
    INSERT INTO dbo.workflow_stage (workflow_definition_id, stage_order, code, label, stage_type, actor_strategy, role_id, assignment_group_id, send_back_target)
    VALUES
        (@incDirect, 10, N'implementor', N'Implementor', N'FULFILMENT', N'IMPLEMENTOR', NULL, @implGrp, NULL),
        (@incDirect, 20, N'confirmation', N'Requester confirmation', N'CONFIRMATION', N'REQUESTER', NULL, NULL, NULL),
        (@incDirect, 30, N'closed', N'Closed', N'CLOSURE', N'SYSTEM', NULL, NULL, NULL);
END

IF NOT EXISTS (SELECT 1 FROM dbo.workflow_stage WHERE workflow_definition_id = @sec)
BEGIN
    INSERT INTO dbo.workflow_stage (workflow_definition_id, stage_order, code, label, stage_type, actor_strategy, role_id, assignment_group_id, send_back_target)
    VALUES
        (@sec, 10, N'hierarchy', N'Reporting hierarchy through HOD', N'APPROVAL', N'DYNAMIC_HIERARCHY_TO_HOD', NULL, NULL, N'PREVIOUS_STAGE'),
        (@sec, 20, N'ciso', N'CISO approval', N'APPROVAL', N'NAMED_ROLE', @ciso, NULL, N'PREVIOUS_STAGE'),
        (@sec, 30, N'implementor', N'Implementor', N'FULFILMENT', N'IMPLEMENTOR', NULL, @implGrp, NULL),
        (@sec, 40, N'confirmation', N'Requester confirmation', N'CONFIRMATION', N'REQUESTER', NULL, NULL, NULL),
        (@sec, 50, N'closed', N'Closed', N'CLOSURE', N'SYSTEM', NULL, NULL, NULL);
END

IF NOT EXISTS (SELECT 1 FROM dbo.workflow_stage WHERE workflow_definition_id = @priv)
BEGIN
    INSERT INTO dbo.workflow_stage (workflow_definition_id, stage_order, code, label, stage_type, actor_strategy, role_id, assignment_group_id, send_back_target)
    VALUES
        (@priv, 10, N'hierarchy', N'Reporting hierarchy through HOD', N'APPROVAL', N'DYNAMIC_HIERARCHY_TO_HOD', NULL, NULL, N'PREVIOUS_STAGE'),
        (@priv, 20, N'ciso', N'CISO approval', N'APPROVAL', N'NAMED_ROLE', @ciso, NULL, N'PREVIOUS_STAGE'),
        (@priv, 30, N'security_team', N'IT Security Team', N'ASSIGNMENT', N'ASSIGNMENT_GROUP', NULL, @secGrp, NULL),
        (@priv, 40, N'implementor', N'Implementor', N'FULFILMENT', N'IMPLEMENTOR', NULL, @implGrp, NULL),
        (@priv, 50, N'confirmation', N'Requester confirmation', N'CONFIRMATION', N'REQUESTER', NULL, NULL, NULL),
        (@priv, 60, N'closed', N'Closed', N'CLOSURE', N'SYSTEM', NULL, NULL, NULL);
END
GO

INSERT INTO dbo.workflow_stage_transition (workflow_stage_id, action_code, remarks_required)
SELECT ws.workflow_stage_id, a.action_code, a.remarks_required
FROM dbo.workflow_stage ws
INNER JOIN dbo.workflow_definition wd ON wd.workflow_definition_id = ws.workflow_definition_id
CROSS JOIN (VALUES
    (N'APPROVAL', N'APPROVE', 1),
    (N'APPROVAL', N'REJECT', 1),
    (N'APPROVAL', N'SEND_BACK', 1),
    (N'ASSIGNMENT', N'ASSIGN', 0),
    (N'ASSIGNMENT', N'REASSIGN', 0),
    (N'FULFILMENT', N'ACCEPT', 0),
    (N'FULFILMENT', N'START', 0),
    (N'FULFILMENT', N'HOLD', 0),
    (N'FULFILMENT', N'RESOLVE', 0),
    (N'FULFILMENT', N'REASSIGN', 0),
    (N'CONFIRMATION', N'APPROVE', 1),
    (N'CONFIRMATION', N'SEND_BACK', 1),
    (N'CLOSURE', N'COMPLETE', 0)
) AS a(stage_type, action_code, remarks_required)
WHERE wd.code IN (N'SR_CHAIN_TO_HOD_CISO_IMPL', N'SR_CHAIN_TO_HOD_IMPL', N'INCIDENT_SD_THEN_IMPL',
                  N'INCIDENT_DIRECT_IMPL', N'SECURITY', N'PRIVILEGED_ACCESS')
  AND ws.stage_type = a.stage_type
  AND NOT EXISTS (
        SELECT 1 FROM dbo.workflow_stage_transition x
        WHERE x.workflow_stage_id = ws.workflow_stage_id AND x.action_code = a.action_code);
GO

/* Rule matrix: lower priority number wins. No rule for INCIDENT_DIRECT_IMPL (inactive). */
INSERT INTO dbo.workflow_rule (name, priority, status_code, condition_json, workflow_definition_id)
SELECT s.name, s.priority, s.status_code, s.condition_json, wd.workflow_definition_id
FROM (VALUES
    (N'Privileged access requests', 10, N'Active', N'{"sub_category":["Privileged Access Request"]}'),
    (N'Cyber Security category', 20, N'Active', N'{"category":["Cyber Security"]}'),
    (N'Security Incident ticket type', 21, N'Active', N'{"ticket_type":["Security Incident"]}'),
    (N'Highly Confidential requests', 22, N'Active', N'{"confidentiality":["Highly Confidential"]}'),
    (N'Incident — Service Desk then Implementor', 30, N'Active', N'{"ticket_type":["Incident"]}'),
    (N'Service Request default (CISO on template)', 40, N'Active', N'{"ticket_type":["Service Request"]}'),
    (N'Catch-all fallback', 999, N'Active', N'{}')
) AS s(name, priority, status_code, condition_json)
INNER JOIN dbo.workflow_definition wd ON wd.version_no = 1 AND (
    (s.priority IN (10) AND wd.code = N'PRIVILEGED_ACCESS')
    OR (s.priority IN (20, 21, 22) AND wd.code = N'SECURITY')
    OR (s.priority = 30 AND wd.code = N'INCIDENT_SD_THEN_IMPL')
    OR (s.priority IN (40, 999) AND wd.code = N'SR_CHAIN_TO_HOD_CISO_IMPL')
)
WHERE NOT EXISTS (SELECT 1 FROM dbo.workflow_rule r WHERE r.priority = s.priority AND r.status_code = N'Active');
GO

MERGE dbo.kb_category AS t
USING (VALUES
    (N'VPN', N'VPN'), (N'EMAIL', N'Email'), (N'PASSWORD', N'Password'), (N'NETWORK', N'Network'),
    (N'LAPTOP', N'Laptop'), (N'APPLICATION', N'Application'), (N'SECURITY', N'Security'),
    (N'OFFICE', N'Microsoft Office'), (N'ERP', N'ERP'), (N'LMS', N'LMS'), (N'OTHER', N'Other')
) AS s(code, name)
ON t.code = s.code
WHEN NOT MATCHED THEN INSERT (code, name) VALUES (s.code, s.name);
GO

IF NOT EXISTS (SELECT 1 FROM dbo.kb_article)
BEGIN
    INSERT INTO dbo.kb_article (kb_category_id, title, body, is_published)
    SELECT kb_category_id,
           N'How to connect to corporate VPN',
           N'Step 1: Install GlobalProtect from the software centre. Step 2: Use your domain credentials. Do not share OTP. Raise an ITSM ticket if connection fails after 15 minutes.',
           1
    FROM dbo.kb_category WHERE code = N'VPN';
END
GO

MERGE dbo.notification_rule AS t
USING (VALUES
    (N'TICKET_CREATED', 1, 1),
    (N'APPROVAL_REQUIRED', 1, 1),
    (N'APPROVED', 1, 1),
    (N'REJECTED', 1, 1),
    (N'SLA_NEAR', 1, 1),
    (N'SLA_BREACHED', 1, 1),
    (N'RESOLVED', 1, 1),
    (N'ASSIGNED', 1, 1)
) AS s(event_code, in_app, send_email)
ON t.event_code = s.event_code
WHEN NOT MATCHED THEN INSERT (event_code, in_app, send_email, is_active)
     VALUES (s.event_code, s.in_app, s.send_email, 1);
GO

MERGE dbo.email_template AS t
USING (VALUES
    (N'TICKET_CREATED', N'[ITSM] Ticket {{ticketNumber}} created', N'Your request {{ticketNumber}} has been submitted.'),
    (N'APPROVAL_REQUIRED', N'[ITSM] Approval required {{ticketNumber}}', N'Please approve or reject {{ticketNumber}}. Remarks are mandatory.'),
    (N'RESOLVED', N'[ITSM] Ticket {{ticketNumber}} resolved', N'Please confirm resolution of {{ticketNumber}}.')
) AS s(code, subject, body)
ON t.code = s.code
WHEN NOT MATCHED THEN INSERT (code, subject, body) VALUES (s.code, s.subject, s.body);
GO

MERGE dbo.system_setting AS t
USING (VALUES
    (N'sla.amber-percent', N'20', N'sla', N'Percent remaining when SLA turns amber'),
    (N'workflow.max-manager-hops', N'12', N'workflow', N'Cap for DYNAMIC_HIERARCHY_TO_HOD expansion'),
    (N'approval.remarks-min-length', N'10', N'workflow', N'Assumed minimum remarks length (Q34)'),
    (N'ui.timezone', N'India Standard Time', N'ui', N'Display time zone')
) AS s(setting_key, setting_value, category, description)
ON t.setting_key = s.setting_key
WHEN MATCHED THEN UPDATE SET setting_value = s.setting_value, description = s.description
WHEN NOT MATCHED THEN INSERT (setting_key, setting_value, category, description, is_secret)
     VALUES (s.setting_key, s.setting_value, s.category, s.description, 0);
GO

IF NOT EXISTS (SELECT 1 FROM dbo.escalation_matrix)
BEGIN
    INSERT INTO dbo.escalation_matrix (trigger_code, priority_code, notify_role_id, notify_group_id)
    SELECT N'NEAR', N'Critical', NULL, assignment_group_id FROM dbo.assignment_group WHERE code = N'IT_SERVICE_DESK'
    UNION ALL
    SELECT N'BREACHED', N'Critical', role_id, NULL FROM dbo.role WHERE code = N'HOD'
    UNION ALL
    SELECT N'BREACHED', N'High', NULL, assignment_group_id FROM dbo.assignment_group WHERE code = N'IT_SERVICE_DESK';
END
GO

PRINT N'master-data.sql complete.';
GO
