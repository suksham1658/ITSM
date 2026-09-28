/*
  Flyway V8 — dbo.notification text columns back to NVARCHAR (as defined in V1).
  SQL Server 2016 / 2019 (compat 130).

  In-app notifications are now written by the application (entity Notification, @Nationalized).
  On one database the table had been rebuilt outside Flyway with VARCHAR columns, which fails
  Hibernate schema validation and cannot store non-Latin names. Each column is altered only when
  it is currently VARCHAR, so databases built from V1 are untouched. Neither column is part of an
  index or constraint, so ALTER COLUMN is enough.
*/
SET NOCOUNT ON;
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
GO

IF EXISTS (SELECT 1 FROM sys.columns c INNER JOIN sys.types t ON t.user_type_id = c.user_type_id
           WHERE c.object_id = OBJECT_ID(N'dbo.notification') AND c.name = N'title' AND t.name = N'varchar')
    ALTER TABLE dbo.notification ALTER COLUMN title NVARCHAR(256) NOT NULL;
GO

IF EXISTS (SELECT 1 FROM sys.columns c INNER JOIN sys.types t ON t.user_type_id = c.user_type_id
           WHERE c.object_id = OBJECT_ID(N'dbo.notification') AND c.name = N'body' AND t.name = N'varchar')
    ALTER TABLE dbo.notification ALTER COLUMN body NVARCHAR(1000) NOT NULL;
GO
