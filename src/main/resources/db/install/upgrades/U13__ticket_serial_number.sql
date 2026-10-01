-- Raise Request: device serial number for Hardware tickets (or "Not available"); replaces Confidentiality on the form.
-- Idempotent: SchemaInstaller runs it at every start on SQL Server without Flyway (e.g. SQL Server 2012 ITSM_PROD).
IF COL_LENGTH(N'dbo.ticket', N'serial_number') IS NULL
    ALTER TABLE dbo.ticket ADD serial_number NVARCHAR(100) NULL;
GO
