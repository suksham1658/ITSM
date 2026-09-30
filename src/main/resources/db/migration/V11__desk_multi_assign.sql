-- IT Service Desk: implementors per category, several implementors per fulfilment step, Reject at the desk.
-- Same statements as db/install/upgrades/U11__desk_multi_assign.sql (run by SchemaInstaller where Flyway is off).
IF OBJECT_ID(N'dbo.category_implementor', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.category_implementor (
        category_id   BIGINT NOT NULL,
        employee_id   BIGINT NOT NULL,
        CONSTRAINT PK_category_implementor PRIMARY KEY CLUSTERED (category_id, employee_id),
        CONSTRAINT FK_catimpl_category FOREIGN KEY (category_id) REFERENCES dbo.category (category_id),
        CONSTRAINT FK_catimpl_employee FOREIGN KEY (employee_id) REFERENCES dbo.employee (employee_id)
    );
END
GO
IF OBJECT_ID(N'dbo.workflow_instance_stage_assignee', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.workflow_instance_stage_assignee (
        workflow_instance_stage_id BIGINT NOT NULL,
        employee_id                BIGINT NOT NULL,
        CONSTRAINT PK_wfis_assignee PRIMARY KEY CLUSTERED (workflow_instance_stage_id, employee_id),
        CONSTRAINT FK_wfisa_stage FOREIGN KEY (workflow_instance_stage_id) REFERENCES dbo.workflow_instance_stage (workflow_instance_stage_id),
        CONSTRAINT FK_wfisa_employee FOREIGN KEY (employee_id) REFERENCES dbo.employee (employee_id)
    );
END
GO
-- Reject at every Assignment (service desk) step, including running tickets (their stages point here).
INSERT INTO dbo.workflow_stage_transition (workflow_stage_id, action_code, remarks_required)
SELECT s.workflow_stage_id, N'REJECT', 1
FROM dbo.workflow_stage s
WHERE s.stage_type = N'ASSIGNMENT'
  AND NOT EXISTS (SELECT 1 FROM dbo.workflow_stage_transition t
                  WHERE t.workflow_stage_id = s.workflow_stage_id AND t.action_code = N'REJECT');
GO
