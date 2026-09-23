/*
  Demo / UAT sample rows (Indian NBFC-style names from the HTML prototype).
  No passwords, hashes, bind DNs, or connection strings.
  Run after master-data.sql. SQL Server 2016 / 2019.
*/
SET NOCOUNT ON;
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
GO

USE [ItsmPortal];
GO

/* ---- employees (insert without hierarchy, then patch manager/hod) ---- */
;WITH src AS (
    SELECT * FROM (VALUES
        (N'EMP30001', N'neha.singh', N'Neha Singh', N'Head – Information Technology', N'IT', N'neha.singh@enterprisenbfc.com'),
        (N'EMP30002', N'rajesh.gupta', N'Rajesh Gupta', N'Head – Finance & HR', N'FIN', N'rajesh.gupta@enterprisenbfc.com'),
        (N'EMP30003', N'sanjeev.bhatt', N'Sanjeev Bhatt', N'Head – Operations & Credit', N'OPS', N'sanjeev.bhatt@enterprisenbfc.com'),
        (N'EMP30004', N'meenakshi.rao', N'Meenakshi Rao', N'Head – Legal, Risk & Compliance', N'LGL', N'meenakshi.rao@enterprisenbfc.com'),
        (N'EMP20131', N'ritika.chawla', N'Ritika Chawla', N'DGM – IT', N'IT', N'ritika.chawla@enterprisenbfc.com'),
        (N'EMP20121', N'vivaan.oberoi', N'Vivaan Oberoi', N'AGM – IT', N'IT', N'vivaan.oberoi@enterprisenbfc.com'),
        (N'EMP20111', N'kiran.desai', N'Kiran Desai', N'Senior Manager – IT', N'IT', N'kiran.desai@enterprisenbfc.com'),
        (N'EMP20101', N'amit.sharma', N'Amit Sharma', N'IT Manager', N'IT', N'amit.sharma@enterprisenbfc.com'),
        (N'EMP20102', N'sunil.verma', N'Sunil Verma', N'Finance Manager', N'FIN', N'sunil.verma@enterprisenbfc.com'),
        (N'EMP20103', N'ritu.kapoor', N'Ritu Kapoor', N'Operations Manager', N'OPS', N'ritu.kapoor@enterprisenbfc.com'),
        (N'EMP20104', N'vivek.malhotra', N'Vivek Malhotra', N'Credit & Collections Manager', N'CRD', N'vivek.malhotra@enterprisenbfc.com'),
        (N'EMP20105', N'pooja.agarwal', N'Pooja Agarwal', N'HR & Admin Manager', N'HR', N'pooja.agarwal@enterprisenbfc.com'),
        (N'EMP20106', N'sanjay.bhatt', N'Sanjay Bhatt', N'Legal, Risk & Compliance Manager', N'LGL', N'sanjay.bhatt@enterprisenbfc.com'),
        (N'EMP40001', N'ananya.krishnan', N'Ananya Krishnan', N'Chief Information Security Officer', N'IT', N'ananya.krishnan@enterprisenbfc.com'),
        (N'EMP40002', N'karan.chopra', N'Karan Chopra', N'Deputy CISO', N'IT', N'karan.chopra@enterprisenbfc.com'),
        (N'EMP50001', N'nitin.saxena', N'Nitin Saxena', N'Service Desk Analyst', N'IT', N'nitin.saxena@enterprisenbfc.com'),
        (N'EMP50002', N'pallavi.nair', N'Pallavi Nair', N'Service Desk Lead', N'IT', N'pallavi.nair@enterprisenbfc.com'),
        (N'EMP60001', N'suresh.kumar', N'Suresh Kumar', N'IT Support Engineer', N'IT', N'suresh.kumar@enterprisenbfc.com'),
        (N'EMP60002', N'ravi.prakash', N'Ravi Prakash', N'Network Engineer', N'IT', N'ravi.prakash@enterprisenbfc.com'),
        (N'EMP60003', N'deepak.yadav', N'Deepak Yadav', N'Systems Engineer', N'IT', N'deepak.yadav@enterprisenbfc.com'),
        (N'EMP70001', N'rakesh.tiwari', N'Rakesh Tiwari', N'IT Administrator', N'IT', N'rakesh.tiwari@enterprisenbfc.com'),
        (N'EMP70002', N'vishal.khanna', N'Vishal Khanna', N'System Administrator', N'IT', N'vishal.khanna@enterprisenbfc.com'),
        (N'EMP10245', N'rajesh.kumar', N'Rajesh Kumar', N'Assistant Manager – IT', N'IT', N'rajesh.kumar@enterprisenbfc.com'),
        (N'EMP10312', N'priya.sharma', N'Priya Sharma', N'Deputy Manager – Finance', N'FIN', N'priya.sharma@enterprisenbfc.com'),
        (N'EMP10318', N'vikram.nair', N'Vikram Nair', N'Senior Executive – Operations', N'OPS', N'vikram.nair@enterprisenbfc.com'),
        (N'EMP10329', N'anjali.desai', N'Anjali Desai', N'Manager – Credit', N'CRD', N'anjali.desai@enterprisenbfc.com')
    ) AS v(employee_no, sam, display_name, designation, dept_code, email)
)
INSERT INTO dbo.employee (employee_no, upn, sam_account_name, display_name, designation, email, department_id, portal_active)
SELECT s.employee_no,
       s.email,
       s.sam,
       s.display_name,
       s.designation,
       s.email,
       d.department_id,
       1
FROM src s
INNER JOIN dbo.department d ON d.code = s.dept_code
WHERE NOT EXISTS (SELECT 1 FROM dbo.employee e WHERE e.employee_no = s.employee_no);
GO

;WITH mgr AS (
    SELECT e.employee_id,
           CASE e.employee_no
               WHEN N'EMP20131' THEN N'EMP30001'
               WHEN N'EMP20121' THEN N'EMP20131'
               WHEN N'EMP20111' THEN N'EMP20121'
               WHEN N'EMP20101' THEN N'EMP20111'
               WHEN N'EMP10245' THEN N'EMP20101'
               WHEN N'EMP20102' THEN N'EMP30002'
               WHEN N'EMP10312' THEN N'EMP20102'
               WHEN N'EMP20103' THEN N'EMP30003'
               WHEN N'EMP10318' THEN N'EMP20103'
               WHEN N'EMP20104' THEN N'EMP30003'
               WHEN N'EMP10329' THEN N'EMP20104'
               WHEN N'EMP20105' THEN N'EMP30002'
               WHEN N'EMP20106' THEN N'EMP30004'
               WHEN N'EMP50001' THEN N'EMP20101'
               WHEN N'EMP50002' THEN N'EMP20101'
               WHEN N'EMP60001' THEN N'EMP20101'
               WHEN N'EMP60002' THEN N'EMP20101'
               WHEN N'EMP60003' THEN N'EMP20101'
               WHEN N'EMP70001' THEN N'EMP20101'
               WHEN N'EMP70002' THEN N'EMP20101'
               ELSE NULL
           END AS manager_no
    FROM dbo.employee e
)
UPDATE e
SET manager_id = m.employee_id
FROM dbo.employee e
INNER JOIN mgr x ON x.employee_id = e.employee_id AND x.manager_no IS NOT NULL
INNER JOIN dbo.employee m ON m.employee_no = x.manager_no;
GO

UPDATE e SET hod_id = h.employee_id
FROM dbo.employee e
INNER JOIN dbo.department d ON d.department_id = e.department_id
INNER JOIN dbo.employee h ON h.employee_no =
    CASE d.code
        WHEN N'IT' THEN N'EMP30001'
        WHEN N'FIN' THEN N'EMP30002'
        WHEN N'HR' THEN N'EMP30002'
        WHEN N'OPS' THEN N'EMP30003'
        WHEN N'CRD' THEN N'EMP30003'
        WHEN N'LGL' THEN N'EMP30004'
        ELSE N'EMP30001'
    END;
GO

/* Clear HOD self-manager */
UPDATE dbo.employee SET manager_id = NULL, hod_id = NULL
WHERE employee_no IN (N'EMP30001', N'EMP30002', N'EMP30003', N'EMP30004', N'EMP40001', N'EMP40002');
GO

DELETE er
FROM dbo.employee_role er
INNER JOIN dbo.employee e ON e.employee_id = er.employee_id
WHERE e.employee_no LIKE N'EMP%';

INSERT INTO dbo.employee_role (employee_id, role_id)
SELECT e.employee_id, r.role_id
FROM dbo.employee e
INNER JOIN dbo.role r ON r.code =
    CASE
        WHEN e.employee_no IN (N'EMP10245', N'EMP10312', N'EMP10318', N'EMP10329') THEN N'EMPLOYEE'
        WHEN e.employee_no LIKE N'EMP201%' THEN N'MANAGER'
        WHEN e.employee_no LIKE N'EMP300%' THEN N'HOD'
        WHEN e.employee_no LIKE N'EMP400%' THEN N'CISO'
        WHEN e.employee_no LIKE N'EMP500%' THEN N'IT_SERVICE_DESK'
        WHEN e.employee_no LIKE N'EMP600%' THEN N'IT_IMPLEMENTOR'
        WHEN e.employee_no = N'EMP70001' THEN N'IT_ADMIN'
        WHEN e.employee_no = N'EMP70002' THEN N'SYSTEM_ADMINISTRATOR'
        ELSE N'EMPLOYEE'
    END;
GO

INSERT INTO dbo.assignment_group_member (assignment_group_id, employee_id)
SELECT g.assignment_group_id, e.employee_id
FROM dbo.assignment_group g
INNER JOIN dbo.employee e ON (
    (g.code = N'IT_SERVICE_DESK' AND e.employee_no IN (N'EMP50001', N'EMP50002'))
    OR (g.code = N'IT_IMPLEMENTORS' AND e.employee_no IN (N'EMP60001', N'EMP60002', N'EMP60003'))
    OR (g.code = N'IT_SECURITY_TEAM' AND e.employee_no IN (N'EMP60002', N'EMP40002'))
)
WHERE NOT EXISTS (
    SELECT 1 FROM dbo.assignment_group_member m
    WHERE m.assignment_group_id = g.assignment_group_id AND m.employee_id = e.employee_id);
GO

IF NOT EXISTS (SELECT 1 FROM dbo.asset WHERE asset_tag = N'AST-LAP-2245')
INSERT INTO dbo.asset (asset_tag, asset_type, serial_number, employee_id, department_id, location, purchase_date, warranty_end, status_code, operating_system, ip_address)
SELECT N'AST-LAP-2245', N'Laptop', N'SN-ITL-2245', e.employee_id, e.department_id, N'Mumbai HO – 4th Floor',
       CAST(N'2024-04-01' AS DATE), CAST(N'2027-04-01' AS DATE), N'Active', N'Windows 11', N'10.20.15.88'
FROM dbo.employee e WHERE e.employee_no = N'EMP10245';

IF NOT EXISTS (SELECT 1 FROM dbo.asset WHERE asset_tag = N'AST-LAP-1188')
INSERT INTO dbo.asset (asset_tag, asset_type, serial_number, employee_id, department_id, location, status_code, operating_system)
SELECT N'AST-LAP-1188', N'Laptop', N'SN-ITL-1188', e.employee_id, e.department_id, N'Mumbai HO – 6th Floor', N'Active', N'Windows 11'
FROM dbo.employee e WHERE e.employee_no = N'EMP10312';
GO

/* ---- Sample tickets + expanded workflow instances ---- */
IF NOT EXISTS (SELECT 1 FROM dbo.ticket WHERE public_number = N'ITSM-2026-001245')
BEGIN
    DECLARE @srType BIGINT = (SELECT ticket_type_id FROM dbo.ticket_type WHERE code = N'SERVICE_REQUEST');
    DECLARE @incType BIGINT = (SELECT ticket_type_id FROM dbo.ticket_type WHERE code = N'INCIDENT');
    DECLARE @accType BIGINT = (SELECT ticket_type_id FROM dbo.ticket_type WHERE code = N'ACCESS_REQUEST');
    DECLARE @net BIGINT = (SELECT category_id FROM dbo.category WHERE code = N'NETWORK');
    DECLARE @hw BIGINT = (SELECT category_id FROM dbo.category WHERE code = N'HARDWARE');
    DECLARE @am BIGINT = (SELECT category_id FROM dbo.category WHERE code = N'ACCESS_MGMT');
    DECLARE @vpn BIGINT = (SELECT sub_category_id FROM dbo.sub_category WHERE code = N'VPN');
    DECLARE @lap BIGINT = (SELECT sub_category_id FROM dbo.sub_category WHERE code = N'LAPTOP');
    DECLARE @priv BIGINT = (SELECT sub_category_id FROM dbo.sub_category WHERE code = N'PRIVILEGED');
    DECLARE @rajesh BIGINT = (SELECT employee_id FROM dbo.employee WHERE employee_no = N'EMP10245');
    DECLARE @priya BIGINT = (SELECT employee_id FROM dbo.employee WHERE employee_no = N'EMP10312');
    DECLARE @anjali BIGINT = (SELECT employee_id FROM dbo.employee WHERE employee_no = N'EMP10329');
    DECLARE @suresh BIGINT = (SELECT employee_id FROM dbo.employee WHERE employee_no = N'EMP60001');
    DECLARE @deepak BIGINT = (SELECT employee_id FROM dbo.employee WHERE employee_no = N'EMP60003');
    DECLARE @implGrp BIGINT = (SELECT assignment_group_id FROM dbo.assignment_group WHERE code = N'IT_IMPLEMENTORS');
    DECLARE @sdGrp BIGINT = (SELECT assignment_group_id FROM dbo.assignment_group WHERE code = N'IT_SERVICE_DESK');
    DECLARE @asset1 BIGINT = (SELECT asset_id FROM dbo.asset WHERE asset_tag = N'AST-LAP-2245');
    DECLARE @srDef BIGINT = (SELECT workflow_definition_id FROM dbo.workflow_definition WHERE code = N'SR_CHAIN_TO_HOD_CISO_IMPL' AND version_no = 1);
    DECLARE @incDef BIGINT = (SELECT workflow_definition_id FROM dbo.workflow_definition WHERE code = N'INCIDENT_SD_THEN_IMPL' AND version_no = 1);
    DECLARE @privDef BIGINT = (SELECT workflow_definition_id FROM dbo.workflow_definition WHERE code = N'PRIVILEGED_ACCESS' AND version_no = 1);
    DECLARE @srRule BIGINT = (SELECT workflow_rule_id FROM dbo.workflow_rule WHERE name = N'Service Request default (CISO on template)');
    DECLARE @incRule BIGINT = (SELECT workflow_rule_id FROM dbo.workflow_rule WHERE name = N'Incident — Service Desk then Implementor');
    DECLARE @privRule BIGINT = (SELECT workflow_rule_id FROM dbo.workflow_rule WHERE name = N'Privileged access requests');
    DECLARE @slaHigh BIGINT = (SELECT sla_policy_id FROM dbo.sla_policy WHERE priority_code = N'High');
    DECLARE @slaCrit BIGINT = (SELECT sla_policy_id FROM dbo.sla_policy WHERE priority_code = N'Critical');
    DECLARE @t1 BIGINT, @t2 BIGINT, @t3 BIGINT;
    DECLARE @i1 BIGINT, @i2 BIGINT, @i3 BIGINT;
    DECLARE @now DATETIME2(3) = SYSUTCDATETIME();

    INSERT INTO dbo.ticket (
        public_number, ticket_type_id, category_id, sub_category_id, subject, description,
        priority_code, impact_code, urgency_code, confidentiality_code, location, asset_id, application_name,
        requester_id, department_id, status_code, progress_code, assigned_group_id)
    SELECT N'ITSM-2026-001245', @srType, @net, @vpn,
           N'VPN Access Required for Client Meetings',
           N'Requested by Rajesh Kumar for client meetings. Please provision GlobalProtect access.',
           N'High', N'Individual', N'High', N'Normal', N'Mumbai HO – 4th Floor', @asset1, N'GlobalProtect VPN',
           @rajesh, (SELECT department_id FROM dbo.employee WHERE employee_id = @rajesh),
           N'Pending Approval', N'pending_hierarchy', @implGrp;
    SET @t1 = SCOPE_IDENTITY();

    INSERT INTO dbo.ticket (
        public_number, ticket_type_id, category_id, sub_category_id, subject, description,
        priority_code, impact_code, urgency_code, confidentiality_code, location,
        requester_id, department_id, status_code, progress_code, assigned_implementor_id, assigned_group_id, major_incident)
    SELECT N'ITSM-2026-001246', @incType, @hw, @lap,
           N'Laptop Not Booting After Windows Update',
           N'Finance user laptop fails to boot after cumulative update.',
           N'High', N'Individual', N'High', N'Normal', N'Mumbai HO – 6th Floor',
           @priya, (SELECT department_id FROM dbo.employee WHERE employee_id = @priya),
           N'In Progress', N'in_progress', @deepak, @implGrp, 0;
    SET @t2 = SCOPE_IDENTITY();

    INSERT INTO dbo.ticket (
        public_number, ticket_type_id, category_id, sub_category_id, subject, description,
        priority_code, impact_code, urgency_code, confidentiality_code, application_name,
        requester_id, department_id, status_code, progress_code)
    SELECT N'ITSM-2026-001247', @accType, @am, @priv,
           N'Privileged DB Access – Credit Risk Schema',
           N'Privileged access to credit risk schema for month-end.',
           N'Critical', N'Department', N'Critical', N'Highly Confidential', N'Core Banking DB',
           @anjali, (SELECT department_id FROM dbo.employee WHERE employee_id = @anjali),
           N'Pending Approval', N'pending_ciso';
    SET @t3 = SCOPE_IDENTITY();

    INSERT INTO dbo.workflow_instance (ticket_id, workflow_definition_id, workflow_rule_id, status_code)
    VALUES (@t1, @srDef, @srRule, N'InProgress');
    SET @i1 = SCOPE_IDENTITY();
    INSERT INTO dbo.workflow_instance (ticket_id, workflow_definition_id, workflow_rule_id, status_code)
    VALUES (@t2, @incDef, @incRule, N'InProgress');
    SET @i2 = SCOPE_IDENTITY();
    INSERT INTO dbo.workflow_instance (ticket_id, workflow_definition_id, workflow_rule_id, status_code)
    VALUES (@t3, @privDef, @privRule, N'InProgress');
    SET @i3 = SCOPE_IDENTITY();

    UPDATE dbo.ticket SET workflow_instance_id = @i1 WHERE ticket_id = @t1;
    UPDATE dbo.ticket SET workflow_instance_id = @i2 WHERE ticket_id = @t2;
    UPDATE dbo.ticket SET workflow_instance_id = @i3 WHERE ticket_id = @t3;

    /* Expanded LDAP hops for Rajesh: Amit → Kiran → Vivaan → Ritika → Neha */
    INSERT INTO dbo.workflow_instance_stage (
        workflow_instance_id, workflow_stage_id, stage_order, code, label, stage_type, actor_strategy,
        resolved_employee_id, status_code, action_code, remarks, acted_at_utc)
    SELECT @i1, ws.workflow_stage_id, 10, N'hop_manager1', N'Manager — Amit Sharma', N'APPROVAL', N'DYNAMIC_HIERARCHY_TO_HOD',
           (SELECT employee_id FROM dbo.employee WHERE employee_no = N'EMP20101'),
           N'Completed', N'APPROVE', N'Business justified for client meetings.', DATEADD(HOUR, -4, @now)
    FROM dbo.workflow_stage ws WHERE ws.workflow_definition_id = @srDef AND ws.code = N'hierarchy';

    INSERT INTO dbo.workflow_instance_stage (
        workflow_instance_id, workflow_stage_id, stage_order, code, label, stage_type, actor_strategy,
        resolved_employee_id, status_code)
    SELECT @i1, ws.workflow_stage_id, v.ord, v.code, v.label, N'APPROVAL', N'DYNAMIC_HIERARCHY_TO_HOD', e.employee_id, v.st
    FROM dbo.workflow_stage ws
    CROSS JOIN (VALUES
        (11, N'hop_manager2', N'Manager — Kiran Desai', N'EMP20111', N'Completed'),
        (12, N'hop_manager3', N'Manager — Vivaan Oberoi', N'EMP20121', N'Pending'),
        (13, N'hop_manager4', N'Manager — Ritika Chawla', N'EMP20131', N'Pending'),
        (14, N'hop_hod', N'HOD — Neha Singh', N'EMP30001', N'Pending')
    ) AS v(ord, code, label, emp, st)
    INNER JOIN dbo.employee e ON e.employee_no = v.emp
    WHERE ws.workflow_definition_id = @srDef AND ws.code = N'hierarchy';

    UPDATE dbo.workflow_instance_stage
    SET action_code = N'APPROVE', remarks = N'Reporting line concurs; proceed.', acted_at_utc = DATEADD(HOUR, -3, @now), status_code = N'Completed'
    WHERE workflow_instance_id = @i1 AND code = N'hop_manager2';

    UPDATE dbo.workflow_instance_stage SET status_code = N'Current'
    WHERE workflow_instance_id = @i1 AND code = N'hop_manager3';

    INSERT INTO dbo.workflow_instance_stage (
        workflow_instance_id, workflow_stage_id, stage_order, code, label, stage_type, actor_strategy,
        resolved_employee_id, resolved_role_id, status_code)
    SELECT @i1, ws.workflow_stage_id, 20, N'ciso', N'CISO approval', N'APPROVAL', N'NAMED_ROLE',
           (SELECT employee_id FROM dbo.employee WHERE employee_no = N'EMP40001'),
           (SELECT role_id FROM dbo.role WHERE code = N'CISO'),
           N'Pending'
    FROM dbo.workflow_stage ws WHERE ws.workflow_definition_id = @srDef AND ws.code = N'ciso';

    INSERT INTO dbo.workflow_instance_stage (
        workflow_instance_id, workflow_stage_id, stage_order, code, label, stage_type, actor_strategy,
        resolved_group_id, status_code)
    SELECT @i1, ws.workflow_stage_id, 30, N'implementor', N'Implementor', N'FULFILMENT', N'IMPLEMENTOR', @implGrp, N'Pending'
    FROM dbo.workflow_stage ws WHERE ws.workflow_definition_id = @srDef AND ws.code = N'implementor';

    INSERT INTO dbo.workflow_instance_stage (
        workflow_instance_id, workflow_stage_id, stage_order, code, label, stage_type, actor_strategy,
        resolved_employee_id, status_code)
    SELECT @i1, ws.workflow_stage_id, 40, N'confirmation', N'Requester confirmation', N'CONFIRMATION', N'REQUESTER', @rajesh, N'Pending'
    FROM dbo.workflow_stage ws WHERE ws.workflow_definition_id = @srDef AND ws.code = N'confirmation';

    INSERT INTO dbo.workflow_instance_stage (
        workflow_instance_id, workflow_stage_id, stage_order, code, label, stage_type, actor_strategy, status_code)
    SELECT @i1, ws.workflow_stage_id, 50, N'closed', N'Closed', N'CLOSURE', N'SYSTEM', N'Pending'
    FROM dbo.workflow_stage ws WHERE ws.workflow_definition_id = @srDef AND ws.code = N'closed';

    UPDATE dbo.workflow_instance SET current_stage_id = (
        SELECT workflow_instance_stage_id FROM dbo.workflow_instance_stage WHERE workflow_instance_id = @i1 AND code = N'hop_manager3')
    WHERE workflow_instance_id = @i1;

    /* Incident: SD completed, implementor current */
    INSERT INTO dbo.workflow_instance_stage (
        workflow_instance_id, workflow_stage_id, stage_order, code, label, stage_type, actor_strategy,
        resolved_group_id, status_code, action_code, remarks, acted_at_utc)
    SELECT @i2, ws.workflow_stage_id, 10, N'servicedesk', N'IT Service Desk triage', N'ASSIGNMENT', N'SERVICE_DESK',
           @sdGrp, N'Completed', N'ASSIGN', N'Assigned to systems engineer Deepak Yadav.', DATEADD(HOUR, -2, @now)
    FROM dbo.workflow_stage ws WHERE ws.workflow_definition_id = @incDef AND ws.code = N'servicedesk';

    INSERT INTO dbo.workflow_instance_stage (
        workflow_instance_id, workflow_stage_id, stage_order, code, label, stage_type, actor_strategy,
        resolved_employee_id, resolved_group_id, status_code)
    SELECT @i2, ws.workflow_stage_id, 20, N'implementor', N'Implementor', N'FULFILMENT', N'IMPLEMENTOR',
           @deepak, @implGrp, N'Current'
    FROM dbo.workflow_stage ws WHERE ws.workflow_definition_id = @incDef AND ws.code = N'implementor';

    INSERT INTO dbo.workflow_instance_stage (
        workflow_instance_id, workflow_stage_id, stage_order, code, label, stage_type, actor_strategy, resolved_employee_id, status_code)
    SELECT @i2, ws.workflow_stage_id, 30, N'confirmation', N'Requester confirmation', N'CONFIRMATION', N'REQUESTER', @priya, N'Pending'
    FROM dbo.workflow_stage ws WHERE ws.workflow_definition_id = @incDef AND ws.code = N'confirmation';

    INSERT INTO dbo.workflow_instance_stage (
        workflow_instance_id, workflow_stage_id, stage_order, code, label, stage_type, actor_strategy, status_code)
    SELECT @i2, ws.workflow_stage_id, 40, N'closed', N'Closed', N'CLOSURE', N'SYSTEM', N'Pending'
    FROM dbo.workflow_stage ws WHERE ws.workflow_definition_id = @incDef AND ws.code = N'closed';

    UPDATE dbo.workflow_instance SET current_stage_id = (
        SELECT workflow_instance_stage_id FROM dbo.workflow_instance_stage WHERE workflow_instance_id = @i2 AND code = N'implementor')
    WHERE workflow_instance_id = @i2;

    INSERT INTO dbo.ticket_sla (ticket_id, sla_policy_id, sla_start_utc, response_due_utc, resolve_due_utc, first_response_utc, state_code)
    VALUES
        (@t1, @slaHigh, DATEADD(HOUR, -5, @now), DATEADD(MINUTE, -5 * 60 + 30, @now), DATEADD(HOUR, 3, @now), DATEADD(HOUR, -4, @now), N'WITHIN'),
        (@t2, @slaHigh, DATEADD(HOUR, -6, @now), DATEADD(MINUTE, -6 * 60 + 30, @now), DATEADD(HOUR, 2, @now), DATEADD(HOUR, -5, @now), N'WITHIN');

    INSERT INTO dbo.ticket_sla (ticket_id, sla_policy_id, sla_start_utc, response_due_utc, resolve_due_utc, state_code)
    VALUES (@t3, @slaCrit, DATEADD(HOUR, -1, @now), DATEADD(MINUTE, -45, @now), DATEADD(HOUR, 3, @now), N'NEAR');

    INSERT INTO dbo.ticket_comment (ticket_id, author_id, body, is_internal)
    VALUES
        (@t1, @rajesh, N'VPN is required for today''s client meeting.', 0),
        (@t2, @deepak, N'Imaging media prepared. Will attempt repair this afternoon.', 1);

    INSERT INTO dbo.notification (recipient_id, ticket_id, title, body, is_read)
    SELECT employee_id, @t1, N'Approval required for ITSM-2026-001245', N'Ticket ITSM-2026-001245 is awaiting your approval. Remarks are mandatory.', 0
    FROM dbo.employee WHERE employee_no = N'EMP20121';

    INSERT INTO dbo.audit_log (occurred_at_utc, employee_id, employee_no, role_code, module_code, action_code, ticket_id, new_value, ip_address, result_code)
    SELECT DATEADD(HOUR, -5, @now), @rajesh, N'EMP10245', N'EMPLOYEE', N'TICKET', N'CREATE_TICKET', @t1, N'ITSM-2026-001245', N'10.20.15.45', N'SUCCESS';

    INSERT INTO dbo.audit_log (occurred_at_utc, employee_id, employee_no, role_code, module_code, action_code, ticket_id, old_value, new_value, ip_address, result_code)
    SELECT DATEADD(HOUR, -4, @now), e.employee_id, N'EMP20101', N'MANAGER', N'APPROVAL', N'APPROVE', @t1, N'Pending', N'Approved', N'10.20.15.10', N'SUCCESS'
    FROM dbo.employee e WHERE e.employee_no = N'EMP20101';

    INSERT INTO dbo.config_change_request (
        change_type, entity_name, entity_key, payload_json, previous_json, description, status_code, requested_by_id)
    SELECT N'SLA_MATRIX', N'sla_policy', N'High',
           N'{"priority_code":"High","response_minutes":30,"resolution_minutes":480}',
           N'{"priority_code":"High","response_minutes":30,"resolution_minutes":480}',
           N'Demo maker-checker row — no effective change',
           N'PendingApproval',
           employee_id
    FROM dbo.employee WHERE employee_no = N'EMP70001';
END
GO

UPDATE dbo.ticket_number_config SET last_allocated = 1247 WHERE sequence_year = 2026 AND last_allocated < 1247;
GO

PRINT N'sample-data.sql complete. No secrets inserted.';
GO
