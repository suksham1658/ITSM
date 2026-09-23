# Enterprise ITSM Portal

Internal IT Service Management portal for an Indian NBFC.

| Layer | Choice |
|-------|--------|
| Language | **Java 8** (`source` / `target` 1.8) |
| App | **Spring Boot 2.7.18** (`javax.*`, Hibernate 5, Thymeleaf 3 / spring5, Security 5) |
| Database | **Microsoft SQL Server 2016 or 2019**, compatibility **130** |
| Package | `com.nbfc.itsm` |

Phases 1–8 built the slice (schema, LDAP/RBAC, tickets, dynamic workflow, SLA, UI, reports, tests). **Phase 9 is a gap review**, not a new module. Honest done/deferred/assumed: see the project document store `docs/phase-9-gap-report.md`.

**Do not** use the `h2` profile in UAT or production. That path is a local preview only.

## Unzip and host (private infrastructure)

1. Unzip the source archive into a directory (example: `itsm-portal/`).
2. Install **JDK 8**, **Maven 3.8+**, and **SQL Server 2016 or 2019**. You also need corporate **LDAP/AD** for login.
3. Copy `env.example` to a file **outside** the repo (never commit filled values). Set `DB_*` and `LDAP_*`.
4. Create database `ItsmPortal` (compat 130). Either run `database/database.sql` then let the app Flyway-migrate, or follow `database/README.md` for a DBA-led script run. Do not run both full `tables.sql` drop/rebuild **and** Flyway on the same live database.
5. Insert the first portal admin (`portal_active` + `employee_role`) with SQL — there is **no** password column. They sign in with their AD password.
6. `export SPRING_PROFILES_ACTIVE=prod` (or `uat`), then `mvn -DskipTests package` and run the jar behind a TLS reverse proxy.

Step-by-step (ports, backup, India/on-prem): `docs/private-infra-hosting.md` in the document store / Context download, next to this zip.

## Run on Java 8

Point `JAVA_HOME` at a JDK 8 (8u400+). Maven `source`/`target` are 1.8; do not compile with a newer `--release` that pulls in APIs the runtime lacks.

```bash
export JAVA_HOME=/path/to/jdk8
export PATH="$JAVA_HOME/bin:$PATH"
mvn test
```

`mvn test` must stay green on JDK 8 (Phase 8: 61 tests).

### Default: LDAP + SQL Server

This is the intended runtime. Create the database (compat 130) and map an instance login to `itsm_app` outside Git (`database/README.md` or Flyway V1–V5 on first boot).

```bash
export DB_URL='jdbc:sqlserver://YOUR_HOST:1433;databaseName=ItsmPortal;encrypt=true;trustServerCertificate=true'
export DB_USERNAME='itsm_app_user'
export DB_PASSWORD='from-your-secret-store'
export LDAP_URL='ldaps://your-dc.example.in:636'
export LDAP_BASE_DN='DC=example,DC=in'
export LDAP_BIND_DN='CN=itsm-bind,OU=Service Accounts,DC=example,DC=in'
export LDAP_BIND_PASSWORD='from-your-secret-store'
export FILE_STORAGE_ROOT=/var/itsm/files
export SESSION_COOKIE_SECURE=true
mvn -DskipTests package
java -jar target/itsm-portal-0.8.0-SNAPSHOT.jar --server.port=43123
```

Login is **corporate LDAP only**. The portal does not reset AD passwords and has no remember-me. The employee must already exist and be `portal_active`; roles are admin-assigned (maker-checker), never self-provisioned.

### H2 preview (no SQL Server, no directory)

Use only on a workstation or agent to click through Raise → workflow. Login works only if `H2_PREVIEW_PASSWORD` is set. Username: **`preview.admin`**. That user is stubbed as HOD-of-self, CISO, service desk and implementor so one login can walk Incident and Service Request. Hibernate `create-drop`; Flyway is off — this is **not** a SQL Server rehearsal.

```bash
export JAVA_HOME=/path/to/jdk8
export H2_PREVIEW_PASSWORD='choose-a-local-secret'
mvn -DskipTests package
java -jar target/itsm-portal-0.8.0-SNAPSHOT.jar --spring.profiles.active=h2 --server.port=43123
```

Open `http://localhost:43123/login`.

## Environment variables

| Variable | Purpose |
|----------|---------|
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | SQL Server (ignored on `h2`) |
| `HIKARI_MAX_POOL` / `HIKARI_MIN_IDLE` | Pool size |
| `LDAP_URL` / `LDAP_BASE_DN` / `LDAP_BIND_DN` / `LDAP_BIND_PASSWORD` | Directory bind |
| `LDAP_USER_SEARCH_FILTER` / `LDAP_USER_DN_PATTERN` / `LDAP_EMPLOYEE_ID_ATTRIBUTE` / `LDAP_MANAGER_ATTRIBUTE` | Optional LDAP mapping |
| `SESSION_TIMEOUT` / `SESSION_ABSOLUTE_TIMEOUT` | Idle / absolute session |
| `SESSION_COOKIE_SECURE` | Set `true` behind HTTPS |
| `FILE_STORAGE_ROOT` / `FILE_STORAGE_TYPE` | Attachments (filesystem) |
| `MAIL_HOST` / `MAIL_PORT` / `MAIL_USERNAME` / `MAIL_PASSWORD` / `MAIL_FROM` | Reserved; outbound mail is not wired |
| `H2_PREVIEW_PASSWORD` | H2 profile only; BCrypt-checked, **not** stored on `employee` |
| `SPRING_PROFILES_ACTIVE` | omit (SQL Server), or `h2` for preview; `dev` / `uat` / `prod` as you name them |
| `SERVER_PORT` | Default `8080` if unset |

No secrets in Git. No AD password reset. No production impersonation / role switcher.

## What this slice does

- Raise **Incident** (service desk → implementor → confirm) and **Service Request** (manager hops to HOD → CISO → implementor → confirm), plus security/privileged seeds. Paths are **workflow data**, not Java `if (type)`.
- Approve / Reject / Send Back require **remarks** (min 10 after trim).
- SLA clocks with business calendar / holidays; lists and reports are **role-scoped** and paginated.
- Admin **Users**: enable/disable and roles via maker-checker.

Deferred (empty or stub UI): knowledge base, notifications/email, asset CMDB, global header search, HOD “IT Requests”, audit-log admin table, most admin editors (roles, SLA config, designer). LDAP `manager` DN is not synced yet — hops need `employee.manager` populated.
