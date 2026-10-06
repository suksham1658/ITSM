/* V19 — widen address columns so they can hold large addresses.
   ALTER COLUMN to the same type is harmless if re-run. */

ALTER TABLE dbo.location ALTER COLUMN address NVARCHAR(2000) NULL;
GO

ALTER TABLE dbo.imac_detail ALTER COLUMN office_address NVARCHAR(2000) NULL;
GO
