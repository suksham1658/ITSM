# ITSM Portal — database scripts (Phase 2)

Microsoft **SQL Server 2016 or 2019** only. Scripts set `COMPATIBILITY_LEVEL = 130` and avoid T-SQL added in 2017+ (`STRING_AGG`, `TRIM`, `CONCAT_WS`, `GENERATE_SERIES`, UTF-8 collations, ledger).

Application stack (later phases): Java 8 / Spring Boot 2.7 — **not** in this folder.

## Run order

Connect as a sysadmin (or a login that can `CREATE DATABASE`). **Do not** put passwords in these files.

```text
1. database.sql          Create ItsmPortal, compat 130, collation SQL_Latin1_General_CP1_CI_AS
2. tables.sql            Tables, PK/FK/CHECK, immutable audit_log trigger
3. constraints.sql       Mandatory remarks trigger (Approve / Reject / Send Back)
4. indexes.sql           Including unique Active workflow_rule.priority
5. master-data.sql       Lookups, RBAC, SLA, calendar, workflow seeds + rule matrix
6. security-data.sql     Roles itsm_app / itsm_readonly / itsm_auditor (no logins)
7. sample-data.sql       Optional demo employees and tickets (UAT/dev only)
```

sqlcmd example (password from environment, never from Git):

```bat
sqlcmd -S tcp:YOUR_HOST,1433 -d master -U %ITSM_SQL_USER% -P %ITSM_SQL_PASSWORD% -I -i database.sql
sqlcmd -S tcp:YOUR_HOST,1433 -d ItsmPortal -U %ITSM_SQL_USER% -P %ITSM_SQL_PASSWORD% -I -i tables.sql
sqlcmd -S tcp:YOUR_HOST,1433 -d ItsmPortal -U %ITSM_SQL_USER% -P %ITSM_SQL_PASSWORD% -I -i constraints.sql
sqlcmd -S tcp:YOUR_HOST,1433 -d ItsmPortal -U %ITSM_SQL_USER% -P %ITSM_SQL_PASSWORD% -I -i indexes.sql
sqlcmd -S tcp:YOUR_HOST,1433 -d ItsmPortal -U %ITSM_SQL_USER% -P %ITSM_SQL_PASSWORD% -I -i master-data.sql
sqlcmd -S tcp:YOUR_HOST,1433 -d ItsmPortal -U %ITSM_SQL_USER% -P %ITSM_SQL_PASSWORD% -I -i security-data.sql
sqlcmd -S tcp:YOUR_HOST,1433 -d ItsmPortal -U %ITSM_SQL_USER% -P %ITSM_SQL_PASSWORD% -I -i sample-data.sql
```

Create the SQL login outside this repo, then:

```sql
CREATE USER [itsm_app_user] FOR LOGIN [itsm_app_login];
ALTER ROLE itsm_app ADD MEMBER [itsm_app_user];
```

## What is seeded

| Item | Notes |
|------|--------|
| `SR_CHAIN_TO_HOD_CISO_IMPL` | **Active** default Service Request: `DYNAMIC_HIERARCHY_TO_HOD` → CISO → Implementor → confirm → close |
| `SR_CHAIN_TO_HOD_IMPL` | Active clone **without** CISO (no matching rule until admin adds one) |
| `INCIDENT_SD_THEN_IMPL` | **Active** default Incident |
| `INCIDENT_DIRECT_IMPL` | **Inactive**, no Active rule |
| `SECURITY` / `PRIVILEGED_ACCESS` | Active, higher-priority rules |
| Catch-all rule priority 999 | Points at default SR template |
| Remarks | `remarks_required = 1` on APPROVE / REJECT / SEND_BACK; trigger enforces non-blank text |
| `employee` vs `employee_role` | Roles are not a column on `employee` |
| Secrets | None. `system_setting.is_secret` must stay 0 |

## Re-running

`tables.sql` drops and recreates all tables (destroys data). Prefer restoring a backup in UAT instead of dropping prod.

`master-data.sql` uses `MERGE` / guarded `INSERT` and will not delete in-flight `workflow_instance` rows.

## Flyway (Phase 3)

The Spring Boot app ships the same objects as versioned migrations (no `USE`, no full DROP rebuild):

`src/main/resources/db/migration/V1__core_tables.sql` … `V5__database_roles.sql`

Dev/UAT sample rows: `src/main/resources/db/dev/V6__sample_data.sql`.

## Design notes

See `docs/phase-2-database.md` in the project document store (Phase 1 architecture remains the product spec).
