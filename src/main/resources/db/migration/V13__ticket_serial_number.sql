-- Raise Request: device serial number for Hardware tickets (or "Not available").
-- Same statements as db/install/upgrades/U13__ticket_serial_number.sql (run by SchemaInstaller where Flyway is off).
IF COL_LENGTH(N'dbo.ticket', N'serial_number') IS NULL
    ALTER TABLE dbo.ticket ADD serial_number NVARCHAR(100) NULL;
GO
