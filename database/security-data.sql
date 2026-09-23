/*
  Database roles and grants. Does NOT create SQL logins or passwords.
  Map an instance login (created outside Git) with:
    ALTER ROLE itsm_app ADD MEMBER [your_app_login];
  SQL Server 2016 / 2019.
*/
SET NOCOUNT ON;
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
GO

USE [ItsmPortal];
GO

IF NOT EXISTS (SELECT 1 FROM sys.database_principals WHERE name = N'itsm_app' AND type = N'R')
    CREATE ROLE [itsm_app] AUTHORIZATION [dbo];
IF NOT EXISTS (SELECT 1 FROM sys.database_principals WHERE name = N'itsm_readonly' AND type = N'R')
    CREATE ROLE [itsm_readonly] AUTHORIZATION [dbo];
IF NOT EXISTS (SELECT 1 FROM sys.database_principals WHERE name = N'itsm_auditor' AND type = N'R')
    CREATE ROLE [itsm_auditor] AUTHORIZATION [dbo];
GO

/* Application: DML on operational tables; INSERT-only on audit_log */
DECLARE @sql NVARCHAR(MAX);
DECLARE @t SYSNAME;

DECLARE c CURSOR LOCAL FAST_FORWARD FOR
    SELECT t.name
    FROM sys.tables t
    INNER JOIN sys.schemas s ON s.schema_id = t.schema_id
    WHERE s.name = N'dbo' AND t.name <> N'audit_log';

OPEN c;
FETCH NEXT FROM c INTO @t;
WHILE @@FETCH_STATUS = 0
BEGIN
    SET @sql = N'GRANT SELECT, INSERT, UPDATE, DELETE ON dbo.' + QUOTENAME(@t) + N' TO [itsm_app];';
    EXEC(@sql);
    SET @sql = N'GRANT SELECT ON dbo.' + QUOTENAME(@t) + N' TO [itsm_readonly];';
    EXEC(@sql);
    SET @sql = N'GRANT SELECT ON dbo.' + QUOTENAME(@t) + N' TO [itsm_auditor];';
    EXEC(@sql);
    FETCH NEXT FROM c INTO @t;
END
CLOSE c;
DEALLOCATE c;
GO

GRANT SELECT, INSERT ON dbo.audit_log TO [itsm_app];
DENY UPDATE, DELETE ON dbo.audit_log TO [itsm_app];
GRANT SELECT ON dbo.audit_log TO [itsm_readonly];
DENY INSERT, UPDATE, DELETE ON dbo.audit_log TO [itsm_readonly];
GRANT SELECT ON dbo.audit_log TO [itsm_auditor];
DENY INSERT, UPDATE, DELETE ON dbo.audit_log TO [itsm_auditor];
GO

REVOKE ALTER, CONTROL ON dbo.audit_log FROM [itsm_app];
GO

PRINT N'security-data.sql complete. Create SQL logins outside this repository and add them to itsm_app.';
GO
