/*
  Flyway callback: runs on the migration connection after every migrate.
  The versioned scripts start with SET NOCOUNT ON. If that connection is ever handed back to the
  application pool, NOCOUNT ON makes UPDATEs report -1 rows and Hibernate then fails with
  "optimistic locking failed". Turn it off again before the connection is released.
*/
SET NOCOUNT OFF;
