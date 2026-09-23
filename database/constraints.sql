/*
  Extra constraints / triggers not inlined in tables.sql
  SQL Server 2016 / 2019 (compat 130). Uses LTRIM/RTRIM (not TRIM).
*/
SET NOCOUNT ON;
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
GO

USE [ItsmPortal];
GO

IF OBJECT_ID(N'dbo.trg_wfis_mandatory_remarks', N'TR') IS NOT NULL
    DROP TRIGGER dbo.trg_wfis_mandatory_remarks;
GO

CREATE TRIGGER dbo.trg_wfis_mandatory_remarks
ON dbo.workflow_instance_stage
AFTER INSERT, UPDATE
AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS (
        SELECT 1
        FROM inserted i
        WHERE i.action_code IN (N'APPROVE', N'REJECT', N'SEND_BACK')
          AND i.status_code IN (N'Completed', N'Rejected')
          AND (i.remarks IS NULL OR LEN(LTRIM(RTRIM(i.remarks))) = 0)
    )
    BEGIN
        ROLLBACK TRANSACTION;
        RAISERROR(N'Remarks are mandatory on Approve, Reject, and Send Back.', 16, 1);
        RETURN;
    END
END
GO

PRINT N'constraints.sql complete.';
GO
