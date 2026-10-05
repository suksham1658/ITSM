/* U17 — IMAC request details table + the "raise IMAC" permission.
   Idempotent; run by SchemaInstaller on SQL Server where Flyway is off. */

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = N'imac_detail' AND schema_id = SCHEMA_ID(N'dbo'))
CREATE TABLE dbo.imac_detail (
    imac_detail_id   BIGINT IDENTITY(1,1) NOT NULL,
    ticket_id        BIGINT NOT NULL,
    username         NVARCHAR(128) NULL,
    user_sapid       NVARCHAR(64)  NULL,
    asset            NVARCHAR(128) NULL,
    make             NVARCHAR(128) NULL,
    model            NVARCHAR(128) NULL,
    grade            NVARCHAR(64)  NULL,
    department       NVARCHAR(128) NULL,
    serial_no        NVARCHAR(128) NULL,
    ram              NVARCHAR(64)  NULL,
    contact_no       NVARCHAR(64)  NULL,
    office_address   NVARCHAR(256) NULL,
    location         NVARCHAR(128) NULL,
    hostname         NVARCHAR(128) NULL,
    CONSTRAINT PK_imac_detail PRIMARY KEY CLUSTERED (imac_detail_id),
    CONSTRAINT UQ_imac_detail_ticket UNIQUE (ticket_id),
    CONSTRAINT FK_imac_detail_ticket FOREIGN KEY (ticket_id) REFERENCES dbo.ticket (ticket_id)
);
GO

/* Permission that lets a user raise IMAC requests (granted by the System Administrator). */
MERGE dbo.permission AS t
USING (VALUES (N'TICKET_RAISE_IMAC', N'Raise IMAC requests')) AS s(code, description)
ON t.code = s.code
WHEN NOT MATCHED THEN INSERT (code, description) VALUES (s.code, s.description);
GO

/* Give it to the System Administrator (final authority) if not already granted. */
INSERT INTO dbo.role_permission (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM dbo.role r
CROSS JOIN dbo.permission p
WHERE r.code = N'SYSTEM_ADMINISTRATOR' AND p.code = N'TICKET_RAISE_IMAC'
  AND NOT EXISTS (SELECT 1 FROM dbo.role_permission rp WHERE rp.role_id = r.role_id AND rp.permission_id = p.permission_id);
GO
