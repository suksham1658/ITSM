-- Implementor view: requester phone / office from AD; assignment history (who assigned to whom, comment).
-- Same statements as db/install/upgrades/U14__implementor_view.sql (run by SchemaInstaller where Flyway is off).
IF COL_LENGTH(N'dbo.employee', N'phone_number') IS NULL
    ALTER TABLE dbo.employee ADD phone_number NVARCHAR(64) NULL;
GO
IF COL_LENGTH(N'dbo.employee', N'office_location') IS NULL
    ALTER TABLE dbo.employee ADD office_location NVARCHAR(256) NULL;
GO
IF OBJECT_ID(N'dbo.ticket_assignment_log', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.ticket_assignment_log (
        ticket_assignment_log_id   BIGINT IDENTITY(1,1) NOT NULL,
        ticket_id                  BIGINT NOT NULL,
        workflow_instance_stage_id BIGINT NULL,
        action_code                NVARCHAR(16) NOT NULL,
        from_employee_id           BIGINT NULL,
        to_employee_id             BIGINT NOT NULL,
        by_employee_id             BIGINT NOT NULL,
        remarks                    NVARCHAR(2000) NULL,
        created_at_utc             DATETIME2(3) NOT NULL,
        CONSTRAINT PK_ticket_assignment_log PRIMARY KEY CLUSTERED (ticket_assignment_log_id),
        CONSTRAINT FK_tal_ticket FOREIGN KEY (ticket_id) REFERENCES dbo.ticket (ticket_id),
        CONSTRAINT FK_tal_from FOREIGN KEY (from_employee_id) REFERENCES dbo.employee (employee_id),
        CONSTRAINT FK_tal_to FOREIGN KEY (to_employee_id) REFERENCES dbo.employee (employee_id),
        CONSTRAINT FK_tal_by FOREIGN KEY (by_employee_id) REFERENCES dbo.employee (employee_id)
    );
    CREATE NONCLUSTERED INDEX IX_tal_ticket ON dbo.ticket_assignment_log (ticket_id, created_at_utc);
END
GO
