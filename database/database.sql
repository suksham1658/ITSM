/*
  Enterprise ITSM Portal — create database
  Target: Microsoft SQL Server 2016 and 2019
  compatibility_level = 130 (SQL Server 2016)

  Run in sqlcmd / SSMS against the instance (not inside ItsmPortal).
  No credentials are stored in this script.
*/
SET NOCOUNT ON;
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
GO

USE [master];
GO

IF DB_ID(N'ItsmPortal') IS NULL
BEGIN
    CREATE DATABASE [ItsmPortal]
        COLLATE SQL_Latin1_General_CP1_CI_AS;
END
GO

ALTER DATABASE [ItsmPortal] SET COMPATIBILITY_LEVEL = 130;
GO

ALTER DATABASE [ItsmPortal] SET READ_COMMITTED_SNAPSHOT ON WITH ROLLBACK IMMEDIATE;
GO

ALTER DATABASE [ItsmPortal] SET ANSI_NULLS ON;
ALTER DATABASE [ItsmPortal] SET QUOTED_IDENTIFIER ON;
ALTER DATABASE [ItsmPortal] SET AUTO_CREATE_STATISTICS ON;
ALTER DATABASE [ItsmPortal] SET AUTO_UPDATE_STATISTICS ON;
GO

USE [ItsmPortal];
GO

PRINT N'ItsmPortal database ready (compatibility_level 130, collation SQL_Latin1_General_CP1_CI_AS, schema dbo).';
GO
