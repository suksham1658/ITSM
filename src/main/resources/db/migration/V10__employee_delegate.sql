-- Delegate (backup approver) on Admin > Users: may act on approvals resolved to the employee.
-- Same statements as db/install/upgrades/U10__employee_delegate.sql (run by SchemaInstaller where Flyway is off).
IF COL_LENGTH(N'dbo.employee', N'delegate_id') IS NULL
    ALTER TABLE dbo.employee ADD delegate_id BIGINT NULL;
GO
IF OBJECT_ID(N'dbo.FK_employee_delegate', N'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.employee ADD CONSTRAINT FK_employee_delegate
        FOREIGN KEY (delegate_id) REFERENCES dbo.employee (employee_id);';
GO
