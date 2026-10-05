/* U16 — IMAC (Install / Move / Add / Change) request type + workflow.
   Idempotent; run by SchemaInstaller on SQL Server where Flyway is off. */

MERGE dbo.ticket_type AS t
USING (VALUES (N'IMAC', N'IMAC', 90)) AS s(code, name, sort_order)
ON t.code = s.code
WHEN NOT MATCHED THEN INSERT (code, name, sort_order) VALUES (s.code, s.name, s.sort_order);
GO

MERGE dbo.category AS t
USING (VALUES (N'IMAC', N'IMAC', 115)) AS s(code, name, sort_order)
ON t.code = s.code
WHEN NOT MATCHED THEN INSERT (code, name, sort_order) VALUES (s.code, s.name, s.sort_order);
GO

;WITH subs AS (
    SELECT c.category_id, v.code, v.name, v.sort_order
    FROM (VALUES
        (N'INSTALL', N'Install', 10),
        (N'MOVE',    N'Move',    20),
        (N'ADD',     N'Add',     30),
        (N'CHANGE',  N'Change',  40)
    ) AS v(code, name, sort_order)
    CROSS JOIN dbo.category c
    WHERE c.code = N'IMAC'
)
MERGE dbo.sub_category AS t
USING subs AS s
ON t.category_id = s.category_id AND t.code = s.code
WHEN NOT MATCHED THEN INSERT (category_id, code, name, sort_order) VALUES (s.category_id, s.code, s.name, s.sort_order);
GO

MERGE dbo.workflow_definition AS t
USING (VALUES
    (N'IMAC_FLOW', N'IMAC — Install / Move / Add / Change', 1, N'Active',
        N'IMAC: manager approval, IT Service Desk, Implementor, requester confirmation, close.')
) AS s(code, name, version_no, status_code, description)
ON t.code = s.code AND t.version_no = s.version_no
WHEN MATCHED THEN UPDATE SET name = s.name, status_code = s.status_code, description = s.description
WHEN NOT MATCHED THEN INSERT (code, name, version_no, status_code, description)
     VALUES (s.code, s.name, s.version_no, s.status_code, s.description);
GO

DECLARE @sdGrp BIGINT = (SELECT assignment_group_id FROM dbo.assignment_group WHERE code = N'IT_SERVICE_DESK');
DECLARE @implGrp BIGINT = (SELECT assignment_group_id FROM dbo.assignment_group WHERE code = N'IT_IMPLEMENTORS');
DECLARE @imac BIGINT = (SELECT workflow_definition_id FROM dbo.workflow_definition WHERE code = N'IMAC_FLOW' AND version_no = 1);

IF @imac IS NOT NULL AND NOT EXISTS (SELECT 1 FROM dbo.workflow_stage WHERE workflow_definition_id = @imac)
BEGIN
    INSERT INTO dbo.workflow_stage (workflow_definition_id, stage_order, code, label, stage_type, actor_strategy, role_id, assignment_group_id, send_back_target)
    VALUES
        (@imac, 10, N'hierarchy',   N'Reporting hierarchy through HOD', N'APPROVAL',     N'DYNAMIC_HIERARCHY_TO_HOD', NULL, NULL,     N'PREVIOUS_STAGE'),
        (@imac, 20, N'servicedesk', N'IT Service Desk',                 N'ASSIGNMENT',   N'SERVICE_DESK',             NULL, @sdGrp,   NULL),
        (@imac, 30, N'implementor', N'Implementor',                     N'FULFILMENT',   N'IMPLEMENTOR',              NULL, @implGrp, NULL),
        (@imac, 40, N'confirmation',N'Requester confirmation',          N'CONFIRMATION', N'REQUESTER',                NULL, NULL,     NULL),
        (@imac, 50, N'closed',      N'Closed',                          N'CLOSURE',      N'SYSTEM',                   NULL, NULL,     NULL);
END
GO

INSERT INTO dbo.workflow_stage_transition (workflow_stage_id, action_code, remarks_required)
SELECT ws.workflow_stage_id, a.action_code, a.remarks_required
FROM dbo.workflow_stage ws
INNER JOIN dbo.workflow_definition wd ON wd.workflow_definition_id = ws.workflow_definition_id
CROSS JOIN (VALUES
    (N'APPROVAL',     N'APPROVE',   1),
    (N'APPROVAL',     N'REJECT',    1),
    (N'APPROVAL',     N'SEND_BACK', 1),
    (N'ASSIGNMENT',   N'ASSIGN',    0),
    (N'ASSIGNMENT',   N'REJECT',    1),
    (N'ASSIGNMENT',   N'REASSIGN',  0),
    (N'FULFILMENT',   N'ACCEPT',    0),
    (N'FULFILMENT',   N'START',     0),
    (N'FULFILMENT',   N'HOLD',      0),
    (N'FULFILMENT',   N'RESOLVE',   0),
    (N'FULFILMENT',   N'REASSIGN',  0),
    (N'CONFIRMATION', N'APPROVE',   1),
    (N'CONFIRMATION', N'SEND_BACK', 1),
    (N'CLOSURE',      N'COMPLETE',  0)
) AS a(stage_type, action_code, remarks_required)
WHERE wd.code = N'IMAC_FLOW'
  AND ws.stage_type = a.stage_type
  AND NOT EXISTS (
        SELECT 1 FROM dbo.workflow_stage_transition x
        WHERE x.workflow_stage_id = ws.workflow_stage_id AND x.action_code = a.action_code);
GO

/* Rule: IMAC ticket type -> IMAC_FLOW (priority 36, between Incident 30 and Service Request 40). */
INSERT INTO dbo.workflow_rule (name, priority, status_code, condition_json, workflow_definition_id)
SELECT N'IMAC requests', 36, N'Active', N'{"ticket_type":["IMAC"]}', wd.workflow_definition_id
FROM dbo.workflow_definition wd
WHERE wd.code = N'IMAC_FLOW' AND wd.version_no = 1
  AND NOT EXISTS (SELECT 1 FROM dbo.workflow_rule r WHERE r.priority = 36 AND r.status_code = N'Active');
GO
