/* U18 — Location master + the "manage locations" permission.
   Idempotent; run by SchemaInstaller on SQL Server where Flyway is off. */

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = N'location' AND schema_id = SCHEMA_ID(N'dbo'))
CREATE TABLE dbo.location (
    location_id  BIGINT IDENTITY(1,1) NOT NULL,
    name         NVARCHAR(128) NOT NULL,
    address      NVARCHAR(512) NULL,
    is_active    BIT NOT NULL CONSTRAINT DF_location_active DEFAULT (1),
    sort_order   INT NOT NULL CONSTRAINT DF_location_sort DEFAULT (0),
    CONSTRAINT PK_location PRIMARY KEY CLUSTERED (location_id),
    CONSTRAINT UQ_location_name UNIQUE (name)
);
GO

MERGE dbo.permission AS t
USING (VALUES (N'LOCATION_MANAGE', N'Manage locations')) AS s(code, description)
ON t.code = s.code
WHEN NOT MATCHED THEN INSERT (code, description) VALUES (s.code, s.description);
GO

INSERT INTO dbo.role_permission (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM dbo.role r
CROSS JOIN dbo.permission p
WHERE r.code = N'SYSTEM_ADMINISTRATOR' AND p.code = N'LOCATION_MANAGE'
  AND NOT EXISTS (SELECT 1 FROM dbo.role_permission rp WHERE rp.role_id = r.role_id AND rp.permission_id = p.permission_id);
GO
