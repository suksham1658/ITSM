-- SLA: real pause (business minutes on hold extend the due times), late-response flag, one-time NEAR / BREACHED alerts.
-- Idempotent: SchemaInstaller runs it at every start on SQL Server without Flyway (e.g. SQL Server 2012 ITSM_PROD).
IF COL_LENGTH(N'dbo.ticket_sla', N'paused_at_utc') IS NULL
    ALTER TABLE dbo.ticket_sla ADD paused_at_utc DATETIME2(3) NULL;
GO
IF COL_LENGTH(N'dbo.ticket_sla', N'paused_minutes') IS NULL
    ALTER TABLE dbo.ticket_sla ADD paused_minutes INT NOT NULL CONSTRAINT DF_tsla_paused_minutes DEFAULT (0);
GO
IF COL_LENGTH(N'dbo.ticket_sla', N'response_breached') IS NULL
    ALTER TABLE dbo.ticket_sla ADD response_breached BIT NOT NULL CONSTRAINT DF_tsla_response_breached DEFAULT (0);
GO
IF COL_LENGTH(N'dbo.ticket_sla', N'near_alerted_utc') IS NULL
    ALTER TABLE dbo.ticket_sla ADD near_alerted_utc DATETIME2(3) NULL;
GO
IF COL_LENGTH(N'dbo.ticket_sla', N'breach_alerted_utc') IS NULL
    ALTER TABLE dbo.ticket_sla ADD breach_alerted_utc DATETIME2(3) NULL;
GO
