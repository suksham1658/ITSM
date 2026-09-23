/*
  Nonclustered indexes — SQL Server 2016 / 2019 (compat 130).
  Run after tables.sql.
*/
SET NOCOUNT ON;
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
GO

USE [ItsmPortal];
GO

IF EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'UQ_workflow_rule_active_priority' AND object_id = OBJECT_ID(N'dbo.workflow_rule'))
    DROP INDEX UQ_workflow_rule_active_priority ON dbo.workflow_rule;
CREATE UNIQUE NONCLUSTERED INDEX UQ_workflow_rule_active_priority
    ON dbo.workflow_rule (priority)
    WHERE status_code = N'Active';
GO

IF EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_employee_department' AND object_id = OBJECT_ID(N'dbo.employee'))
    DROP INDEX IX_employee_department ON dbo.employee;
CREATE NONCLUSTERED INDEX IX_employee_department ON dbo.employee (department_id) INCLUDE (employee_no, display_name, portal_active);

IF EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_employee_manager' AND object_id = OBJECT_ID(N'dbo.employee'))
    DROP INDEX IX_employee_manager ON dbo.employee;
CREATE NONCLUSTERED INDEX IX_employee_manager ON dbo.employee (manager_id);

IF EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_employee_hod' AND object_id = OBJECT_ID(N'dbo.employee'))
    DROP INDEX IX_employee_hod ON dbo.employee;
CREATE NONCLUSTERED INDEX IX_employee_hod ON dbo.employee (hod_id);

IF EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_employee_sam' AND object_id = OBJECT_ID(N'dbo.employee'))
    DROP INDEX IX_employee_sam ON dbo.employee;
CREATE NONCLUSTERED INDEX IX_employee_sam ON dbo.employee (sam_account_name) WHERE sam_account_name IS NOT NULL;

IF EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_ticket_requester' AND object_id = OBJECT_ID(N'dbo.ticket'))
    DROP INDEX IX_ticket_requester ON dbo.ticket;
CREATE NONCLUSTERED INDEX IX_ticket_requester ON dbo.ticket (requester_id, created_at_utc DESC);

IF EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_ticket_status' AND object_id = OBJECT_ID(N'dbo.ticket'))
    DROP INDEX IX_ticket_status ON dbo.ticket;
CREATE NONCLUSTERED INDEX IX_ticket_status ON dbo.ticket (status_code, priority_code) INCLUDE (public_number, subject);

IF EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_ticket_type_cat' AND object_id = OBJECT_ID(N'dbo.ticket'))
    DROP INDEX IX_ticket_type_cat ON dbo.ticket;
CREATE NONCLUSTERED INDEX IX_ticket_type_cat ON dbo.ticket (ticket_type_id, category_id, sub_category_id);

IF EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_ticket_implementor' AND object_id = OBJECT_ID(N'dbo.ticket'))
    DROP INDEX IX_ticket_implementor ON dbo.ticket;
CREATE NONCLUSTERED INDEX IX_ticket_implementor ON dbo.ticket (assigned_implementor_id) WHERE assigned_implementor_id IS NOT NULL;

IF EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_ticket_department' AND object_id = OBJECT_ID(N'dbo.ticket'))
    DROP INDEX IX_ticket_department ON dbo.ticket;
CREATE NONCLUSTERED INDEX IX_ticket_department ON dbo.ticket (department_id, status_code);

IF EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_wfis_instance_status' AND object_id = OBJECT_ID(N'dbo.workflow_instance_stage'))
    DROP INDEX IX_wfis_instance_status ON dbo.workflow_instance_stage;
CREATE NONCLUSTERED INDEX IX_wfis_instance_status ON dbo.workflow_instance_stage (workflow_instance_id, status_code) INCLUDE (resolved_employee_id, stage_order);

IF EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_wfis_actor' AND object_id = OBJECT_ID(N'dbo.workflow_instance_stage'))
    DROP INDEX IX_wfis_actor ON dbo.workflow_instance_stage;
CREATE NONCLUSTERED INDEX IX_wfis_actor ON dbo.workflow_instance_stage (resolved_employee_id, status_code) WHERE resolved_employee_id IS NOT NULL;

IF EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_audit_occurred' AND object_id = OBJECT_ID(N'dbo.audit_log'))
    DROP INDEX IX_audit_occurred ON dbo.audit_log;
CREATE NONCLUSTERED INDEX IX_audit_occurred ON dbo.audit_log (occurred_at_utc DESC, action_code);

IF EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_audit_ticket' AND object_id = OBJECT_ID(N'dbo.audit_log'))
    DROP INDEX IX_audit_ticket ON dbo.audit_log;
CREATE NONCLUSTERED INDEX IX_audit_ticket ON dbo.audit_log (ticket_id, occurred_at_utc DESC) WHERE ticket_id IS NOT NULL;

IF EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_audit_employee' AND object_id = OBJECT_ID(N'dbo.audit_log'))
    DROP INDEX IX_audit_employee ON dbo.audit_log;
CREATE NONCLUSTERED INDEX IX_audit_employee ON dbo.audit_log (employee_no, occurred_at_utc DESC);

IF EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_notification_recipient' AND object_id = OBJECT_ID(N'dbo.notification'))
    DROP INDEX IX_notification_recipient ON dbo.notification;
CREATE NONCLUSTERED INDEX IX_notification_recipient ON dbo.notification (recipient_id, is_read, created_at_utc DESC);

IF EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_ccr_status' AND object_id = OBJECT_ID(N'dbo.config_change_request'))
    DROP INDEX IX_ccr_status ON dbo.config_change_request;
CREATE NONCLUSTERED INDEX IX_ccr_status ON dbo.config_change_request (status_code, requested_at_utc DESC);

IF EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_ticket_comment_ticket' AND object_id = OBJECT_ID(N'dbo.ticket_comment'))
    DROP INDEX IX_ticket_comment_ticket ON dbo.ticket_comment;
CREATE NONCLUSTERED INDEX IX_ticket_comment_ticket ON dbo.ticket_comment (ticket_id, created_at_utc);

IF EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_asset_employee' AND object_id = OBJECT_ID(N'dbo.asset'))
    DROP INDEX IX_asset_employee ON dbo.asset;
CREATE NONCLUSTERED INDEX IX_asset_employee ON dbo.asset (employee_id) WHERE employee_id IS NOT NULL;
GO

PRINT N'indexes.sql complete.';
GO
