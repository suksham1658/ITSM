-- Speed for many users: current workflow steps by status/type (Approvals, queues, dashboard), newest-first
-- ticket lists, implementor offers and SLA counts. Idempotent: SchemaInstaller runs it at every start on SQL Server without Flyway.
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_wfis_status_type' AND object_id = OBJECT_ID(N'dbo.workflow_instance_stage'))
    CREATE NONCLUSTERED INDEX IX_wfis_status_type ON dbo.workflow_instance_stage (status_code, stage_type)
        INCLUDE (workflow_instance_id, resolved_employee_id, resolved_role_id, resolved_group_id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_ticket_created' AND object_id = OBJECT_ID(N'dbo.ticket'))
    CREATE NONCLUSTERED INDEX IX_ticket_created ON dbo.ticket (created_at_utc DESC, ticket_id DESC);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_wfisa_employee' AND object_id = OBJECT_ID(N'dbo.workflow_instance_stage_assignee'))
    CREATE NONCLUSTERED INDEX IX_wfisa_employee ON dbo.workflow_instance_stage_assignee (employee_id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_ticket_sla_state' AND object_id = OBJECT_ID(N'dbo.ticket_sla'))
    CREATE NONCLUSTERED INDEX IX_ticket_sla_state ON dbo.ticket_sla (state_code) INCLUDE (ticket_id);
GO
