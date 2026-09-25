/*
  Flyway V7 — repair dbo.workflow_rule and restore the default workflow rule matrix.
  SQL Server 2016 / 2019 (compat 130). (V6 is reserved for db/dev sample data.)

  Why: without an Active rule every submit fails with "No active workflow rule matches this
  request". On one database the table had also been rebuilt outside Flyway without IDENTITY on
  workflow_rule_id, so inserts failed with "Cannot insert the value NULL into column
  'workflow_rule_id'".

  Step 1 rebuilds the table exactly as in V1 (+ V3 index), but ONLY when workflow_rule_id is not
  an IDENTITY column AND the table is empty; if it has rows the migration stops with an error
  instead of risking data loss. Healthy databases skip step 1 entirely.
  Step 2 inserts each default rule only when no Active rule holds its priority, so rules an
  administrator has edited are left alone.
*/
SET NOCOUNT ON;
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
GO

IF COLUMNPROPERTY(OBJECT_ID(N'dbo.workflow_rule'), N'workflow_rule_id', 'IsIdentity') = 0
BEGIN
    IF EXISTS (SELECT 1 FROM dbo.workflow_rule)
        THROW 50007, N'V7: dbo.workflow_rule.workflow_rule_id is not an IDENTITY column and the table has rows. Repair it manually (restore the V1 definition) and restart.', 1;

    /* Drop every foreign key that points at workflow_rule (normally FK_wfi_rule on workflow_instance). */
    DECLARE @dropFk NVARCHAR(MAX) = N'';
    SELECT @dropFk = @dropFk + N'ALTER TABLE ' + QUOTENAME(OBJECT_SCHEMA_NAME(fk.parent_object_id)) + N'.'
                   + QUOTENAME(OBJECT_NAME(fk.parent_object_id)) + N' DROP CONSTRAINT ' + QUOTENAME(fk.name) + N';'
    FROM sys.foreign_keys fk
    WHERE fk.referenced_object_id = OBJECT_ID(N'dbo.workflow_rule');
    EXEC sys.sp_executesql @dropFk;

    DROP TABLE dbo.workflow_rule;

    CREATE TABLE dbo.workflow_rule (
        workflow_rule_id         BIGINT IDENTITY(1,1) NOT NULL,
        name                     NVARCHAR(128) NOT NULL,
        priority                 INT NOT NULL,
        status_code              NVARCHAR(32) NOT NULL,
        condition_json           NVARCHAR(MAX) NOT NULL,
        workflow_definition_id   BIGINT NOT NULL,
        CONSTRAINT PK_workflow_rule PRIMARY KEY CLUSTERED (workflow_rule_id),
        CONSTRAINT FK_workflow_rule_def FOREIGN KEY (workflow_definition_id) REFERENCES dbo.workflow_definition (workflow_definition_id),
        CONSTRAINT CK_workflow_rule_status CHECK (status_code IN (N'Active', N'Inactive', N'PendingChecker')),
        CONSTRAINT CK_workflow_rule_json CHECK (ISJSON(condition_json) = 1)
    );

    /* Dynamic SQL so these compile against the NEW table, not the one dropped above. */
    EXEC sys.sp_executesql N'CREATE UNIQUE NONCLUSTERED INDEX UQ_workflow_rule_active_priority
        ON dbo.workflow_rule (priority) WHERE status_code = N''Active'';';

    IF COL_LENGTH(N'dbo.workflow_instance', N'workflow_rule_id') IS NOT NULL
        EXEC sys.sp_executesql N'ALTER TABLE dbo.workflow_instance
            ADD CONSTRAINT FK_wfi_rule FOREIGN KEY (workflow_rule_id) REFERENCES dbo.workflow_rule (workflow_rule_id);';
END
GO

INSERT INTO dbo.workflow_rule (name, priority, status_code, condition_json, workflow_definition_id)
SELECT s.name, s.priority, s.status_code, s.condition_json, wd.workflow_definition_id
FROM (VALUES
    (N'Privileged access requests', 10, N'Active', N'{"sub_category":["Privileged Access Request"]}'),
    (N'Cyber Security category', 20, N'Active', N'{"category":["Cyber Security"]}'),
    (N'Security Incident ticket type', 21, N'Active', N'{"ticket_type":["Security Incident"]}'),
    (N'Highly Confidential requests', 22, N'Active', N'{"confidentiality":["Highly Confidential"]}'),
    (N'Incident — Service Desk then Implementor', 30, N'Active', N'{"ticket_type":["Incident"]}'),
    (N'Service Request default (CISO on template)', 40, N'Active', N'{"ticket_type":["Service Request"]}'),
    (N'Catch-all fallback', 999, N'Active', N'{}')
) AS s(name, priority, status_code, condition_json)
INNER JOIN dbo.workflow_definition wd ON wd.version_no = 1 AND wd.status_code = N'Active' AND (
    (s.priority IN (10) AND wd.code = N'PRIVILEGED_ACCESS')
    OR (s.priority IN (20, 21, 22) AND wd.code = N'SECURITY')
    OR (s.priority = 30 AND wd.code = N'INCIDENT_SD_THEN_IMPL')
    OR (s.priority IN (40, 999) AND wd.code = N'SR_CHAIN_TO_HOD_CISO_IMPL')
)
WHERE NOT EXISTS (SELECT 1 FROM dbo.workflow_rule r WHERE r.priority = s.priority AND r.status_code = N'Active');
GO
