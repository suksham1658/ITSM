# ITSM Portal — Application Guide

Developer and support reference: architecture, modules and their flows, every HTTP endpoint,
the main classes and methods, configuration (database, LDAP, security), the database schema,
and how to debug problems.

> Secrets: never copy passwords into this document. `application.yml` still contains fallback
> credentials for the database and the LDAP bind account; in any shared environment set the
> environment variables listed in [§4](#4-configuration) and remove those fallbacks.

---

## 1. At a glance

| Item | Value |
|---|---|
| Type | Server-rendered web app (Spring MVC + Thymeleaf), one WAR: runs embedded (`java -jar`) or on external Tomcat 9 |
| Java | 1.8 (source/target 1.8) |
| Framework | Spring Boot 2.7.18 (Spring Security 5.7, Hibernate 5.6, `javax.*`) |
| Database | Microsoft SQL Server 2016/2019 (compat level 130), schema managed by Flyway 9.22.3 |
| Identity | Corporate Active Directory via LDAP simple bind (JNDI) |
| Build | Maven (`pom.xml`), no wrapper |
| Base package | `com.nbfc.itsm` |
| Default port | `8092` (`SERVER_PORT`) |

Request path through the app:

```
Browser ─► Spring Security filter chain ─► Controller (web/*) ─► Service (ticket, workflow, admin, …)
                │                                                     │
                │  login: FailClosedLdapAuthenticationProvider        ├─► Spring Data repositories (domain/*)
                │         └─► LdapDirectoryClient ─► Active Directory │        └─► SQL Server (Flyway schema)
                │         └─► PortalUserService (employee + roles)    └─► AuditRecorder / NotificationService
                │  every request: PrincipalRefreshFilter (reload roles)
                └─► Thymeleaf templates (templates/*) + static/css, static/js
```

---

## 2. Build, run, test

```bash
# Tests (H2 in-memory + in-memory LDAP; never touch SQL Server or AD)
mvn test
mvn test -Dtest=IncidentFlowTest          # one class

# Package
mvn -DskipTests clean package             # -> target/itsm-portal.war (see 2.1)

# Run against SQL Server + LDAP (default profile; Flyway migrates on start)
java -jar target/itsm-portal.war

# Local preview without SQL Server / AD (H2, no Flyway) — never on a shared server
H2_PREVIEW_PASSWORD=choose-one java -jar target/itsm-portal.war --spring.profiles.active=h2
#   login: preview.admin / the H2_PREVIEW_PASSWORD value
```

Known test state: `PortalUserServiceTest.unknownDirectoryUserIsNotSelfProvisioned` and
`LdapLoginTest.ldapLoginRejectedWhenNotProvisioned` fail **by design of the current login**: unknown
LDAP users are created automatically (`PortalUserService.loadActivePrincipal`). Update or remove
those two tests if auto-creation is the intended behaviour.

### 2.1 Deployment (embedded vs external Tomcat)

The build produces **`target/itsm-portal.war`**, usable both ways (`ItsmPortalApplication` extends
`SpringBootServletInitializer`):

| Where | How | URL |
|---|---|---|
| Local / IDE | run `ItsmPortalApplication.main`, or `java -jar target/itsm-portal.war` (embedded Tomcat) | `http://localhost:8092/` |
| Server | copy `itsm-portal.war` to `<TOMCAT>/webapps/` of **Tomcat 9.x** (not 10+: the app uses `javax.servlet`), Java 8+ | `http://server:8080/itsm-portal/` |

Build: `mvn -DskipTests clean package` (the two known failing tests in §2 otherwise stop the build).

On external Tomcat the `server.*` settings (`server.port`, `server.servlet.session.timeout`, session cookie)
**do not apply** — Tomcat owns them: port in `conf/server.xml`, idle timeout in `conf/web.xml`
(`<session-timeout>30</session-timeout>`), HTTPS/secure cookie on the connector or reverse proxy.
`SESSION_ABSOLUTE_TIMEOUT` still works (it is an application filter).

Configuration on the server: set environment variables (or `-D` system properties) in
`<TOMCAT>/bin/setenv.bat` / `setenv.sh`, e.g. Windows:

```bat
set "DB_URL=jdbc:sqlserver://NEWHOST\INSTANCE;databaseName=ItsmPortal;encrypt=true;trustServerCertificate=true"
set "SPRING_DATASOURCE_USERNAME=itsm_app_login"
set "SPRING_DATASOURCE_PASSWORD=..."
set "LDAP_URL=ldap://dc.example.local:389"
set "LDAP_BASE_DN=dc=example,dc=local"
set "LDAP_BIND_DN=svc-itsm@example.local"
set "LDAP_BIND_PASSWORD=..."
set "FILE_STORAGE_ROOT=D:\itsm\files"
set "ITSM_BOOTSTRAP_ADMINS=your.ad.username"
```

(Environment variables override `application.yml`, including the hard-coded datasource fallbacks.)
Logs go to Tomcat's console / `logs/catalina.*.log`. Health check: `GET /itsm-portal/actuator/health`
(LDAP/mail health contributors are disabled because they would always report DOWN).

### 2.2 New database

1. On the new SQL Server create an **empty** database (only the database — the `dbo` schema exists by
   default; do **not** create tables): `CREATE DATABASE ItsmPortal; ALTER DATABASE ItsmPortal SET COMPATIBILITY_LEVEL = 130;`
2. Create a SQL login + database user with **db_owner** on that database (Flyway creates tables and, in V5,
   database roles).
3. Point `DB_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` at it and start the app.
   Flyway runs V1–V8: all tables, master data (roles, permissions, categories, SLA, workflows, rules).
   Hibernate (`ddl-auto=validate`) only checks the result.
4. The database must be empty on first start: with `baseline-on-migrate=true`, a database that already has
   objects is *baselined* and V1 (the tables) is skipped.

### 2.3 First System Administrator

On a new database nobody has a role. Set `ITSM_BOOTSTRAP_ADMINS` to your AD username (or employee number),
start, and log in with that account: it is granted **SYSTEM_ADMINISTRATOR + EMPLOYEE** (log line
`BOOTSTRAP: … granted SYSTEM_ADMINISTRATOR`, audit `BOOTSTRAP_ADMIN`). This only happens while no active
System Administrator exists. Then clear the variable and assign everyone else's roles in Admin → Users.

Manual alternative (after that person has logged in once):

```sql
INSERT INTO dbo.employee_role (employee_id, role_id, assigned_at_utc)
SELECT e.employee_id, r.role_id, SYSUTCDATETIME()
FROM dbo.employee e JOIN dbo.role r ON r.code IN (N'SYSTEM_ADMINISTRATOR', N'EMPLOYEE')
WHERE e.sam_account_name = N'<ad.username>'
  AND NOT EXISTS (SELECT 1 FROM dbo.employee_role x WHERE x.employee_id = e.employee_id AND x.role_id = r.role_id);
```

---

## 3. Profiles

| Profile | File | Database | Flyway | Notes |
|---|---|---|---|---|
| *(default)* | `application.yml` | SQL Server | `db/migration` | Normal runtime |
| `dev` | `application-dev.yml` | SQL Server | `db/migration` + `db/dev` (V6 sample data) | DEBUG logging for `com.nbfc.itsm` |
| `uat` | `application-uat.yml` | SQL Server | `db/migration` + `db/dev` | Secure cookie |
| `prod` | `application-prod.yml` | SQL Server | `db/migration` only | WARN root log, health only |
| `h2` | `application-h2.yml` | H2 in memory, `create-drop` | off | Preview login (`H2PreviewAuthenticationProvider`, `H2PreviewDataLoader`) |
| `test` | `src/test/resources/application-test.yml` | H2 | off | Used by all Spring tests |

---

## 4. Configuration

### 4.1 Environment variables

| Variable | Default in `application.yml` | Purpose |
|---|---|---|
| `DB_URL` | `jdbc:sqlserver://AUTHDDRN0102\AUTHUM;databaseName=ItsmPortal;encrypt=true;trustServerCertificate=true` | JDBC URL |
| — | `spring.datasource.username` / `password` (hard-coded fallback) | DB login — move to env/secret store |
| `HIKARI_MAX_POOL` / `HIKARI_MIN_IDLE` | 20 / 2 | Connection pool |
| `LDAP_URL` | `ldap://AUTHPDC.Authum.local:389` | Directory server (prefer `ldaps://…:636`) |
| `LDAP_BASE_DN` | `dc=Authum,dc=local` | Search base |
| `LDAP_BIND_DN` / `LDAP_BIND_PASSWORD` | service account (fallback in yml) | Used to find the user's DN |
| `LDAP_USER_SEARCH_FILTER` | `(sAMAccountName={0})` | How the user is found |
| `LDAP_USER_DN_PATTERN` | *(empty)* | If set, bind directly as this DN pattern instead of searching |
| `LDAP_EMPLOYEE_ID_ATTRIBUTE` | `employeeID` | Becomes `employee.employee_no` |
| `LDAP_MANAGER_ATTRIBUTE` | `manager` | Reporting line (approval chain) |
| `SESSION_TIMEOUT` / `SESSION_ABSOLUTE_TIMEOUT` | 30m / 8h | Idle / absolute session lifetime |
| `SESSION_COOKIE_SECURE` | false | Set `true` behind HTTPS |
| `FILE_STORAGE_TYPE` / `FILE_STORAGE_ROOT` | filesystem / *(empty)* | Attachment storage (must be set for uploads) |
| `MAIL_*` | empty | Reserved; e-mail is **not** sent yet |
| `H2_PREVIEW_PASSWORD` | empty | `h2` profile only |
| `ITSM_BOOTSTRAP_ADMINS` | empty | First System Administrator on a new DB (see §2.3) |
| `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD` | yml fallback | Override the DB login on a server |
| `SERVER_PORT` | 8092 | HTTP port |

### 4.2 Database configuration (`spring.datasource`, `spring.jpa`, `spring.flyway`)

- Driver `com.microsoft.sqlserver.jdbc.SQLServerDriver` (mssql-jdbc 11.2.3.jre8); Hikari pool `itsm-hikari`.
- `spring.jpa.hibernate.ddl-auto=validate` — Hibernate **checks** the schema at start-up and refuses to
  start on a mismatch (e.g. `varchar` vs `nvarchar`). It never changes the schema.
- Dialect `SQLServer2012Dialect`, JDBC time zone UTC, `open-in-view=false` (load lazy data inside services).
- Flyway: `classpath:db/migration`, `baseline-on-migrate=true`.
  **`spring.flyway.user/password` are set on purpose** so Flyway gets its own connection; see
  [§11 — NOCOUNT](#nocount-optimistic-locking-failed).

### 4.3 LDAP configuration (`itsm.ldap.*`, class `ItsmProperties.Ldap`)

`url, base-dn, bind-dn, bind-password, user-search-filter, user-dn-pattern, employee-id-attribute,
manager-attribute, connect-timeout-ms (3000), read-timeout-ms (5000)`. LDAP is "configured" when
`url` is not blank; otherwise LDAP login is skipped and the fallback provider rejects the login.

### 4.4 Other `itsm.*` settings

`itsm.storage.type/root`, `itsm.mail.from`, `itsm.security.test-login-enabled`,
`itsm.security.preview-password`, `itsm.security.session-absolute-timeout`.

### 4.5 Settings stored in the database (`dbo.system_setting`)

| Key | Default | Used by |
|---|---|---|
| `workflow.max-manager-hops` | 12 | `WorkflowEngine.managerHops` — max levels up the reporting line |
| `approval.remarks-min-length` | 10 | `WorkflowEngine.requireRemarks` |
| `sla.amber-percent` | 20 | SLA "near" threshold |
| `ui.timezone` | India Standard Time | Display |

---

## 5. Security

### 5.1 Filter chain (`security/SecurityConfig`)

Order of the interesting pieces:

1. `AbsoluteSessionTimeoutFilter` — ends sessions older than `session-absolute-timeout`.
2. Form login `POST /login` → providers in order:
   `FailClosedLdapAuthenticationProvider` → `H2PreviewAuthenticationProvider` (h2 only) →
   `FailClosedFallbackAuthenticationProvider` (always rejects).
3. `PrincipalRefreshFilter` (before `ExceptionTranslationFilter`) — on every page request reloads the
   user's roles/permissions from the DB; ends the session if the employee is disabled/removed.
4. URL rules (`antMatchers(...).hasAuthority(...)`) + method security (`@PreAuthorize`).

Headers: CSP `script-src 'self'` (**inline `onclick` is blocked** — use `data-action` + `itsm.js`),
frame deny, referrer same-origin, CSRF on for all POSTs. Logout `POST /logout` → `/login?logout`.

### 5.2 Principal, roles and permissions

- `ItsmUserPrincipal` holds every active role assigned in `employee_role`, plus an optional active
  role from the **role switcher**. Authorities = permission codes + `ROLE_<code>`.
- Roles never come from LDAP. Permissions per role live in `role_permission`.
- `System Administrator` holds all permissions and can: assign/remove roles **directly** (no approval),
  delete unused custom roles, set reporting lines and group membership.

| Permission | Unlocks |
|---|---|
| TICKET_CREATE | Raise Request |
| TICKET_VIEW_OWN | My Tickets |
| TICKET_VIEW_TEAM / _DEPARTMENT / _SECURITY | Team / Department / Security Requests (+ Risk) |
| TICKET_VIEW_QUEUE_ALL | Ticket Queue, Assignment |
| TICKET_ASSIGN | Assign on the Service Desk step |
| TICKET_APPROVE_ASSIGNED_STAGE | Approvals page, approve/reject/send back |
| TICKET_FULFIL | My Assigned Tickets, Work Queue, Change Requests |
| SLA_MONITOR | SLA Monitoring, Escalations |
| REPORT_VIEW | Reports |
| KB_READ, AUDIT_VIEW, ASSET_MANAGE | Knowledge Base, Audit Trail, Assets |
| ADMIN_USER_MANAGE | Admin → Users, Roles & Permissions |
| ADMIN_MASTERDATA_PROPOSE / _APPROVE | Propose / approve config changes (maker-checker) |
| ADMIN_SYSTEM | System settings |

Seeded roles: EMPLOYEE, MANAGER, HOD, CISO, IT_SERVICE_DESK, IT_IMPLEMENTOR, IT_ADMIN, SYSTEM_ADMINISTRATOR.

---

## 6. Modules and flows

### 6.1 Login and identity (`security/*`, `identity/*`)

```
POST /login ─► FailClosedLdapAuthenticationProvider.authenticate
   ├─ length checks (≤256)                               → "Invalid credentials"
   ├─ LdapDirectoryClient.authenticateAndLoad(user, pwd)
   │    ├─ resolveUserDn: bind as service account (LDAP_BIND_DN) → search filter → bind as user
   │    ├─ searchPerson: read sAMAccountName, employeeID, displayName, mail, title, manager, department
   │    └─ loadManagerChain: follow `manager` DN upwards (≤10 levels)
   │    (each failed step logs "LDAP step failed: <step> | url | principal | cause: <diagnosis>")
   └─ PortalUserService.loadActivePrincipal(person)
        ├─ find employee by employee_no, else sAMAccountName; create if missing (portal_active=1, no roles)
        ├─ update name/email/title; syncHierarchy → manager_id chain + department (match by name/code)
        └─ toPrincipal → roles + permissions from DB
```

Failures: bad password / unreachable AD → `/login?error`; `portal_active=0` → `/login?error=denied`;
DB error after a good bind → logged as "LDAP bind OK … but loading/creating the portal employee
profile failed (database step, not LDAP)".

Role switcher: `POST /session/active-role` (`RoleSwitchController`) → `PortalUserService.switchActiveRole`
(only roles the user holds; audited `ROLE_SWITCH`).

### 6.2 Tickets (`ticket/*`, `web/TicketController`)

- **Raise**: `POST /tickets/raise` → `TicketService.save` → `applyForm` (validation via `Validation` +
  `FieldLimits`) → number from `TicketNumberService.allocate` (`ITSM-YYYY-nnnnnn`) →
  `WorkflowEngine.startOnSubmit`. `intent=draft` saves a `DRAFT-…` ticket without a workflow.
- **View**: `GET /tickets/{id}` → `TicketService.detail` (checks `canView`) → stages, allowed actions,
  "Now with …", assignee list (`WorkflowEngine.assigneePool` + `GroupMembershipService.activeMembers`).
- **Act**: `POST /tickets/{id}/action` → `TicketService.applyAction` → `WorkflowEngine.applyAction`.
- **Comment / attach / download**: `POST /tickets/{id}/comments`, `POST /tickets/{id}/attachments`
  (`AttachmentService.store` — size/type/MIME policy, executables blocked),
  `GET /tickets/{id}/attachments/{aid}`.

Who can **view** a ticket (`TicketService.canView`): requester, assigned implementor, queue/audit/system
permission holders, same-department team/department viewers, security viewers for security tickets,
and whoever can act on the **current** step.

### 6.3 Workflow engine (`workflow/*`)

**Rule matching** — `WorkflowMatcherService.match`: first `Active` rule by `priority` (lowest first) whose
`condition_json` matches (`ticket_type`, `category`, `sub_category`, `confidentiality`, `department`,
`priority`; `{}` matches everything). No match → `WORKFLOW_NO_MATCH`.

| Priority | Condition | Workflow |
|---|---|---|
| 10 | sub-category *Privileged Access Request* | PRIVILEGED_ACCESS: chain → CISO → IT Security Team → Implementor → confirm → close |
| 20–22 | category *Cyber Security* / type *Security Incident* / *Highly Confidential* | SECURITY: chain → CISO → Implementor → confirm → close |
| 30 | type *Incident* | INCIDENT_SD_THEN_IMPL: Service Desk → Implementor → confirm → close |
| 40 | type *Service Request* | SR_CHAIN_TO_HOD_CISO_IMPL: chain → CISO → Implementor → confirm → close |
| 999 | `{}` catch-all | SR_CHAIN_TO_HOD_CISO_IMPL |

**Expansion** — `startOnSubmit` copies template stages into `workflow_instance_stage`. A
`DYNAMIC_HIERARCHY_TO_HOD` stage becomes one approval step **per manager** from `employee.manager_id`
upwards (`managerHops`), stopping at the first person with the HOD role (or the requester's `hod_id`),
capped by `workflow.max-manager-hops`. Step label = the manager's display name.

**Stage types and actions** (`workflow_stage_transition`):

| Stage type | Who acts | Actions | Effect |
|---|---|---|---|
| APPROVAL | named person (manager hop) or role (CISO) | APPROVE, REJECT, SEND_BACK (remarks ≥10) | next step / ticket Rejected / previous step |
| ASSIGNMENT | Service Desk (group or role) | ASSIGN, REASSIGN | picks from **next step's group** (IT Implementors); moves on |
| FULFILMENT | assigned implementor / group | ACCEPT, START, HOLD, REASSIGN, RESOLVE | In Progress / On Hold / hand over / to confirmation |
| CONFIRMATION | requester | APPROVE (closes), SEND_BACK (reopens) | Closed / back to implementor |
| CLOSURE | system | — | ticket Closed |

Ticket status: Draft → Pending Approval / Approved (in desk queue) → Assigned → In Progress ⇄ On Hold →
Resolved → Closed, or Rejected.

**Groups** — `GroupMembershipService.isMember/activeMembers`: a person is in a work group if added on
Admin → Users **or** holds the linked role (IT_SERVICE_DESK ↔ IT Service Desk, IT_IMPLEMENTOR ↔ IT Implementors).

### 6.4 Notifications (`notification/NotificationService`, `web/NotificationController`)

Written in the same transaction as the workflow change; never sent to the person who acted;
`notification_rule.in_app` switches respected.

| Event | Recipient |
|---|---|
| submitted | requester |
| step waiting (approval / desk queue / implementor / confirmation) | the step's person, group members, role holders, or requester |
| approved, declined, sent back, assigned, in progress, on hold | requester |
| assigned to you / reassigned | implementor |
| closed | requester and implementor |
| comment | requester (not for internal notes) and implementor |

UI: header bell (unread count + latest 8, `UiModelAdvice.notifUnread/notifLatest`),
`GET /notifications`, `GET /notifications/{id}/open` (marks read, recipient only), `POST /notifications/read-all`.

### 6.5 SLA (`sla/SlaService`)

`startClocks` on submit (policy by priority, business calendar/holidays), `markFirstResponse` on first
assign/start, `pause` on hold, `markResolved`, `refresh`/`refreshState` → `WITHIN / NEAR / BREACHED / MET`.

### 6.6 Administration (`admin/*`, `web/Admin*Controller`)

- **Users** — `AdminUserService`: list/get, `proposePortalActive`, `proposeRole` (System Administrator:
  applied immediately and recorded as *Applied*; others: *PendingApproval*), `approve`/`reject`
  (maker ≠ checker, lockout guard: someone must keep ADMIN_USER_MANAGE and ADMIN_MASTERDATA_APPROVE).
- **Reporting line & groups** — `EmployeeSetupService.updateReportingLine` (no self/loops/inactive
  managers), `updateGroups`. System Administrator only.
- **Roles & Permissions** — `RoleAdminService`: `list/get/formFor`, `proposeCreate/proposeUpdate`
  (maker-checker via `config_change_request`), `apply` (on approval), `deleteBlockers/delete`
  (System Administrator; only unused custom roles). `PermissionCatalog` = labels/pages per permission.
- **Workflow config** — `AdminCatalogController.updateRule` (name, Active/Inactive, condition must be a JSON object).
- **Config approvals** — `/admin/change-requests`: approve/reject pending changes.

### 6.6.1 AD Account Unlock (`admin/AdAccountUnlockService`, `web/AdAccountController`)

For IT Service Desk and System Administrator (permission `AD_ACCOUNT_UNLOCK`, sidebar **AD Account Unlock**).

1. Search by login ID (prefix), employee ID or part of the name (`itsm.ldap.account-search-filter`, max 25 hits).
2. Open an account: lockout, locked since, failed attempts, last failed attempt, enabled/disabled,
   password expired, password last set (all read with the service account; times in IST).
3. **Unlock account** (only shown when locked): remarks of at least 10 characters are required.
   `LdapDirectoryClient.unlock` looks the DN up again and writes `lockoutTime = 0`, then re-reads the status.
4. Audit row `AD_ACCOUNT / UNLOCK` (SUCCESS or FAILED, with account, actor and remarks); the account owner
   gets an in-app notification if they have a portal profile.

The portal never locks, disables, enables or resets passwords. "Locked" comes from
`msDS-User-Account-Control-Computed` (bit 0x10), falling back to `lockoutTime > 0`.

**AD prerequisites:** `LDAP_BIND_DN` / `LDAP_BIND_PASSWORD` must be set (preferably a dedicated service
account, not an employee's ID), and the AD team must delegate **Read/Write lockoutTime** on the user OUs
to it. Without that, Unlock shows "The portal's service account … is not allowed to unlock accounts".
LDAPS (`ldaps://…:636`) is recommended.

### 6.7 Reports and dashboard (`reporting/ReportingService`, `web/ReportController`)

`dashboard(user)` builds KPI cards and charts; `run(user, code, filter, page, size)` for report codes:
`volume, sla, category, dept, implementor, restime, reopen, aging, pendingapproval, security, asset, monthly`.
Exports: `/reports/{code}.csv`, `/reports/{code}.xls`. Access: `REPORT_VIEW` (+ `assertReportAllowed` per report).

### 6.8 Audit (`audit/LoggingAuditRecorder`)

`record(module, action, detail, result[, request])` and `recordTicket(action, ticketId, old, new)` write
`dbo.audit_log` in their own transaction (REQUIRES_NEW) and log `audit module=… action=…`. The table is
append-only (trigger `trg_audit_log_immutable`). Actions include LOGIN, LOGOUT, ROLE_SWITCH, PROPOSE,
APPROVE, REJECT, ROLE_ASSIGN/REMOVE, ROLE_CREATE/UPDATE/DELETE, REPORTING_LINE, GROUPS, ticket actions.

### 6.9 Cross-cutting helpers

- `validation/Validation`, `validation/FieldLimits` — one message listing every field problem; limits
  mirror DB columns and are exposed to templates as `${limits}` (HTML `required/minlength/maxlength`).
- `web/UiText` (`${@ui…}`) — status → badge colour, legacy step-label clean-up.
- `web/BackLinks` — safe "Back to …" targets (same-host list pages only).
- `web/SearchText` — trims/caps search text.
- `exception/GlobalExceptionHandler` — `ItsmException` → 400 page, access denied → 403, others → 500 + audit.

---

## 7. HTTP endpoints

All require login except `/login`, static assets and `/actuator/health|info`. All POSTs need the CSRF token.

| Method | Path | Handler | Access |
|---|---|---|---|
| GET | `/login` | LoginController.login | public |
| POST | `/login`, `/logout` | Spring Security | public / logged in |
| POST | `/session/active-role` | RoleSwitchController.switchRole | logged in |
| GET | `/` | PortalPageController.dashboard | logged in |
| GET | `/approvals` | PortalPageController.approvals | TICKET_APPROVE_ASSIGNED_STAGE |
| GET | `/queue/desk`, `/queue/assignment` | PortalPageController.desk / assignment | TICKET_VIEW_QUEUE_ALL |
| GET | `/queue/mine`, `/queue/work`, `/queue/implementation` | PortalPageController | TICKET_FULFIL |
| GET | `/sla`, `/escalations` | PortalPageController | SLA_MONITOR |
| GET | `/risk` | PortalPageController.risk | TICKET_VIEW_SECURITY |
| GET | `/kb`, `/audit`, `/assets` | PortalPageController | KB_READ / AUDIT_VIEW / ASSET_MANAGE |
| GET | `/ad-accounts?q=`, `/ad-accounts/account?id=` | AdAccountController.search / account | AD_ACCOUNT_UNLOCK |
| POST | `/ad-accounts/unlock` | AdAccountController.unlock (params `id`, `remarks`) | AD_ACCOUNT_UNLOCK |
| GET | `/tickets` | TicketController.myTickets | logged in |
| GET | `/tickets/team`, `/department`, `/security`, `/changes` | TicketController | VIEW_TEAM / VIEW_DEPARTMENT / VIEW_SECURITY / FULFIL |
| GET, POST | `/tickets/raise` | TicketController.raise / raiseSubmit | TICKET_CREATE |
| GET | `/tickets/{id}` | TicketController.detail | canView |
| POST | `/tickets/{id}/action` | TicketController.action | step actor (params `actionCode`, `remarks`, `assigneeId`) |
| POST | `/tickets/{id}/comments` | TicketController.comment | canView |
| POST | `/tickets/{id}/attachments` | TicketController.attach | canView |
| GET | `/tickets/{id}/attachments/{aid}` | TicketController.download | canView |
| GET | `/notifications` | NotificationController.list | logged in |
| GET | `/notifications/{id}/open` | NotificationController.open | recipient |
| POST | `/notifications/read-all` | NotificationController.readAll | logged in |
| GET | `/reports`, `/reports/{code}`, `/reports/{code}.csv`, `/reports/{code}.xls` | ReportController | REPORT_VIEW |
| GET | `/admin/users`, `/admin/users/{id}` | AdminUserController | ADMIN_USER_MANAGE |
| POST | `/admin/users/{id}/propose-active`, `/propose-role` | AdminUserController | ADMIN_MASTERDATA_PROPOSE |
| POST | `/admin/users/{id}/reporting-line`, `/groups` | AdminUserController | ROLE_SYSTEM_ADMINISTRATOR |
| GET | `/admin/change-requests` | AdminUserController.queue | ADMIN_MASTERDATA_APPROVE |
| POST | `/admin/change-requests/{id}/approve`, `/reject` | AdminUserController | ADMIN_MASTERDATA_APPROVE |
| GET | `/admin/roles`, `/admin/roles/{id}` | AdminRoleController | ADMIN_USER_MANAGE |
| GET, POST | `/admin/roles/new`, `/admin/roles`, `/admin/roles/{id}/edit`, `/admin/roles/{id}` | AdminRoleController | ADMIN_USER_MANAGE + ADMIN_MASTERDATA_PROPOSE |
| GET, POST | `/admin/roles/{id}/delete` | AdminRoleController | ROLE_SYSTEM_ADMINISTRATOR |
| GET | `/admin/categories`, `/admin/sla`, `/admin/workflow`, `/admin/workflow/{id}`, `/admin/config` | AdminCatalogController | ADMIN_MASTERDATA_PROPOSE |
| POST | `/admin/workflow/rules/{id}` | AdminCatalogController.updateRule | ADMIN_MASTERDATA_PROPOSE |
| GET | `/403` | AccessDeniedController | any |
| GET | `/actuator/health`, `/actuator/info` | Spring Boot Actuator | public (details hidden) |

### Debug endpoints

- `GET /actuator/health` — `{"status":"UP"}` when the app and DB pool are healthy.
- `GET /actuator/info` — app name/phase/versions.
- There is intentionally **no** endpoint that exposes config, sessions or env. For deeper diagnosis use
  logs ([§10](#10-logging)) and the SQL in [§11](#11-troubleshooting).

---

## 8. Classes by package (main methods)

| Package / class | Responsibility | Key methods |
|---|---|---|
| `ItsmPortalApplication` | Spring Boot entry point | `main` |
| **config** `ItsmProperties` | `itsm.*` settings (ldap, storage, mail, security) | `Ldap.isConfigured()` |
| `JpaConfig`, `TimeConfig`, `PasswordEncoderConfig`, `WebMvcConfig` | JPA auditing (UTC), clock, BCrypt, `/home` redirect | — |
| `H2PreviewDataLoader` (h2) | Demo org + tickets for preview | `run` |
| **security** `SecurityConfig` | Filter chain, URL rules, headers, login/logout | `securityFilterChain` |
| `FailClosedLdapAuthenticationProvider` | LDAP login; any error ⇒ "Invalid credentials" (+ diagnosis log) | `authenticate` |
| `H2PreviewAuthenticationProvider` / `FailClosedFallbackAuthenticationProvider` | Preview login / always-deny | `authenticate` |
| `ItsmUserPrincipal` | Signed-in user: roles, active role, authorities | `has`, `getRoleCodes`, `getAssignedRoles`, `fingerprint` |
| `PrincipalRefreshFilter` | Reload roles each request; end session if disabled | `doFilterInternal` |
| `AbsoluteSessionTimeoutFilter` | Max session age | `doFilterInternal` |
| `AuditLoginSuccessHandler` / `AuditLoginFailureHandler` | Audit login results, redirects | `onAuthentication*` |
| **identity** `LdapDirectoryClient` | JNDI bind/search, manager chain, error diagnosis | `authenticateAndLoad`, `loadManagerChain`, `diagnose` |
| `PortalUserService` | Directory person → employee + principal | `loadActivePrincipal`, `syncHierarchy`, `toPrincipal`, `refresh`, `switchActiveRole` |
| **ticket** `TicketService` | Ticket CRUD, visibility, queues, comments | `save`, `submitDraft`, `applyAction`, `addComment`, `detail`, `search`, `queueByStageType`, `assignedTo`, `approvalsFor`, `canView`, `requireView` |
| `TicketNumberService` | Public number allocation | `allocate` |
| `AttachmentService` | Attachment policy + filesystem storage | `store`, `resolveFile` |
| **workflow** `WorkflowEngine` | Rule → stages, actions, status, notifications | `startOnSubmit`, `applyAction`, `assertCanAct`, `isActor`, `managerHops`, `assigneePool`, `loadStages` |
| `WorkflowMatcherService` | Condition JSON matching | `match` |
| `GroupMembershipService` | Group members incl. linked role | `isMember`, `activeMembers` |
| **notification** `NotificationService` | In-app notifications per event | `submitted`, `stepIsWaiting`, `approved`, `rejected`, `sentBack`, `assigned`, `statusUpdate`, `closed`, `commented` |
| **sla** `SlaService` | SLA clocks | `startClocks`, `markFirstResponse`, `pause`, `markResolved`, `refresh` |
| **admin** `AdminUserService` | Users, role/active changes, maker-checker | `proposeRole`, `proposePortalActive`, `approve`, `reject`, `pending` |
| `RoleAdminService` | Roles & permissions | `list`, `get`, `proposeCreate`, `proposeUpdate`, `apply`, `delete` |
| `EmployeeSetupService` | Reporting line, groups | `view`, `updateReportingLine`, `updateGroups` |
| `PermissionCatalog`, `RoleForm` | Permission labels; role form | `describe`, `groups` |
| **reporting** `ReportingService` | Dashboard + 12 reports | `dashboard`, `run`, `definitions`, `assertReportAllowed` |
| **audit** `LoggingAuditRecorder` | `audit_log` writer | `record`, `recordTicket` |
| **seed** `CatalogSeedService` | Seeds catalog in H2/tests | `ensureSeeded`, `seed` |
| **validation** `Validation`, `FieldLimits` | Field validation + limits | `text`, `oneOf`, `required`, `throwIfInvalid` |
| **web** controllers | See [§7](#7-http-endpoints) | — |
| `UiModelAdvice` | Model data on every page: `currentUser`, `limits`, `notifUnread`, `notifLatest`, `pendingConfigCount` | — |
| `UiText`, `BackLinks`, `SearchText` | View helpers | — |
| **exception** `GlobalExceptionHandler`, `ItsmException(code, message)` | Error pages | `handle*` |
| **domain** | JPA entities + Spring Data repositories (one per table in [§9](#9-database)) | — |

Front end: `templates/` (Thymeleaf pages, `fragments/` = head, header, sidebar, back link, alerts, modal),
`static/css/itsm.css`, `static/js/itsm.js` (all behaviour via `data-action`, form checks, counters).

---

## 9. Database

### 9.1 Migrations (`src/main/resources/db/migration`)

| Version | Purpose |
|---|---|
| V1 | Core tables (see below) |
| V2 | Trigger `trg_wfis_mandatory_remarks` (remarks required on approve/reject/send back) |
| V3 | Indexes (incl. unique Active `workflow_rule.priority`) |
| V4 | Master data: departments, roles, permissions, ticket types, categories, SLA, calendar, workflows, rules, notification rules, settings |
| V5 | DB roles `itsm_app`, `itsm_readonly`, `itsm_auditor` (no logins) |
| V6 (`db/dev`) | Sample data — dev/uat profiles only |
| V7 | Rebuilds `workflow_rule` with IDENTITY if it was recreated without (only when empty) + restores default rules |
| V8 | `notification.title/body` back to NVARCHAR if they were VARCHAR |
| V9 | Permission `AD_ACCOUNT_UNLOCK`, granted to IT_SERVICE_DESK and SYSTEM_ADMINISTRATOR |
| `afterMigrate.sql` | Callback: `SET NOCOUNT OFF` after migrating |

Never edit an applied migration (Flyway checksum validation fails); add a new `V10__…` instead.
`database/*.sql` are DBA scripts mirroring the migrations — don't run them on a Flyway-managed DB.

### 9.2 Tables

Column-by-column reference with look-up queries: [DATABASE_TABLES.md](DATABASE_TABLES.md).

- **People & access:** `employee` (manager_id, hod_id, department_id, portal_active), `department`, `role`,
  `permission`, `role_permission`, `employee_role`, `assignment_group`, `assignment_group_member`, `ldap_sync_log`.
- **Catalog:** `ticket_type`, `category`, `sub_category`, `sla_policy`, `ticket_number_config`,
  `attachment_policy`, `business_calendar`, `holiday`, `system_setting`.
- **Workflow:** `workflow_definition`, `workflow_stage`, `workflow_stage_transition`, `workflow_rule`,
  `workflow_instance`, `workflow_instance_stage`.
- **Tickets:** `ticket`, `ticket_sla`, `ticket_comment`, `ticket_attachment`, `comment_attachment`, `ticket_relation`.
- **Notifications:** `notification`, `notification_rule`, `email_template`.
- **Governance:** `config_change_request` (maker-checker), `audit_log` (append-only).
- **Not used by code yet:** `asset`, `escalation_matrix`, `escalation_event`, `kb_*`.

Text columns are NVARCHAR in the migrations; String entity fields that map NVARCHAR columns need
`@Nationalized` or `columnDefinition = "nvarchar(...)"`, otherwise `ddl-auto=validate` stops start-up.

---

## 10. Logging

- Console via `logback-spring.xml`, pattern `yyyy-MM-dd HH:mm:ss.SSS UTC [thread] LEVEL logger - msg`.
- Useful loggers: `com.nbfc.itsm.identity.LdapDirectoryClient` (LDAP steps),
  `com.nbfc.itsm.security.FailClosedLdapAuthenticationProvider` (login failures with stack trace),
  `com.nbfc.itsm.workflow.WorkflowMatcherService` (no-rule details), `org.flywaydb` (migrations),
  `org.hibernate.SQL` (set to DEBUG to see SQL), `org.springframework.security` (set to DEBUG for access decisions).
- Enable for one run: `--logging.level.org.springframework.security=DEBUG --logging.level.org.hibernate.SQL=DEBUG`.
- Known leftover: `System.out.println` blocks in `LdapDirectoryClient.searchPerson` and
  `PortalUserService.createEmployeeFromLdap` print every LDAP attribute — remove before production.

---

## 11. Troubleshooting

| Symptom | Likely cause | Check / fix |
|---|---|---|
| "Sign-in failed" | See log line `LDAP step failed: …` | Cause text: CANNOT CONNECT (URL/firewall/TLS), BIND REJECTED + AD code (52e wrong password, 525 no user, 775 locked, 533 disabled, 532/701 expired, 773 must reset), USER NOT FOUND (base DN / filter) |
| `/login?error=denied` | `employee.portal_active = 0` | Admin → Users → enable |
| App won't start: `Schema-validation: wrong column type` | DB column type ≠ entity mapping (e.g. nvarchar vs varchar) | Align via a new migration or `@Nationalized` |
| "No approval workflow is set up…" | No Active rule / rules deleted | Admin → Workflow Config (red banner); V7 restores defaults on start |
| "…no manager is set for you" | `employee.manager_id` NULL | AD `manager` attribute, or Admin → Users → Reporting line |
| Approval chain goes to the CEO | Nobody in the chain has the HOD role | Assign HOD role |
| Service desk sees ticket but no **Take action** | Not in IT Service Desk (group or role) | Assign IT_SERVICE_DESK role |
| "Nobody is in IT Implementors" | No implementors | Assign IT_IMPLEMENTOR role |
| Approver can't open Approvals page | Missing TICKET_APPROVE_ASSIGNED_STAGE | Give the Employee role |
| <a id="nocount-optimistic-locking-failed"></a>`optimistic locking failed … Employee#…` right after a restart | `SET NOCOUNT ON` from a migration leaked into a pooled connection (UPDATE reports -1 rows) | Fixed by `spring.flyway.user/password` + `afterMigrate.sql`; restart |
| Buttons do nothing | Inline JS blocked by CSP | Use `data-action` handled in `itsm.js`; hard refresh (Ctrl+F5) |

Diagnostic SQL (read-only):

```sql
-- Migrations applied
SELECT installed_rank, version, description, success, installed_on FROM dbo.flyway_schema_history ORDER BY installed_rank;

-- Active rules and their workflows
SELECT r.priority, r.status_code, r.name, r.condition_json, d.code, d.status_code
FROM dbo.workflow_rule r JOIN dbo.workflow_definition d ON d.workflow_definition_id = r.workflow_definition_id ORDER BY r.priority;

-- A user's roles, manager and department
SELECT e.employee_id, e.employee_no, e.display_name, e.portal_active, m.display_name AS manager, e.department_id,
       STUFF((SELECT ',' + ro.code FROM dbo.employee_role er JOIN dbo.role ro ON ro.role_id = er.role_id
              WHERE er.employee_id = e.employee_id FOR XML PATH('')), 1, 1, '') AS roles
FROM dbo.employee e LEFT JOIN dbo.employee m ON m.employee_id = e.manager_id WHERE e.employee_no = '<EMP_NO>';

-- Where a ticket is and who must act
SELECT t.public_number, t.status_code, s.stage_order, s.label, s.stage_type, s.status_code,
       p.display_name AS person, ro.code AS role, g.code AS grp
FROM dbo.ticket t JOIN dbo.workflow_instance wi ON wi.ticket_id = t.ticket_id
JOIN dbo.workflow_instance_stage s ON s.workflow_instance_id = wi.workflow_instance_id
LEFT JOIN dbo.employee p ON p.employee_id = s.resolved_employee_id
LEFT JOIN dbo.role ro ON ro.role_id = s.resolved_role_id
LEFT JOIN dbo.assignment_group g ON g.assignment_group_id = s.resolved_group_id
WHERE t.public_number = '<ITSM-…>' ORDER BY s.stage_order;

-- Recent audit trail
SELECT TOP 50 audit_log_id, occurred_at_utc, module_code, action_code, result_code, new_value, employee_no FROM dbo.audit_log ORDER BY audit_log_id DESC;

-- Unread notifications for a user
SELECT n.created_at_utc, n.title, n.is_read FROM dbo.notification n
JOIN dbo.employee e ON e.employee_id = n.recipient_id WHERE e.employee_no = '<EMP_NO>' ORDER BY n.notification_id DESC;
```

---

## 12. Tests (`src/test/java`)

| Area | Test classes |
|---|---|
| Login / LDAP | `LdapLoginTest`, `LdapHierarchySyncTest`, `LdapDiagnosisTest`, `FailClosedLdapAuthenticationProviderTest`, `LoginPageTest` |
| Identity / roles | `PortalUserServiceTest`, `RoleSwitchTest`, `NewUserOnboardingTest`, `RoleAdminServiceTest`, `AdminRolePagesTest`, `EmployeeSetupServiceTest` |
| Tickets / workflow | `TicketLifecycleIntegrationTest`, `CoreItsmServiceTest`, `RequestRoutingTest`, `IncidentFlowTest`, `WorkflowHierarchyTest`, `WorkflowMatcherServiceTest`, `QueueAssignPageTest`, `TicketQueryRepositoryTest`, `AttachmentServiceTest` |
| Notifications | `NotificationFlowTest` |
| Validation / UI | `ValidationTest`, `FormValidationTest`, `PageShellTest`, `BackLinksTest`, `UiTextTest`, `TemplatePresenceTest` |
| Other | `SlaServiceTest`, `ReportingServiceTest`, `SqlServer2016MigrationTest`, `TimeUtcTest`, `ItsmPortalApplicationTests` |

---

## 13. Known gaps

- E-mail notifications not wired (`MAIL_*` reserved).
- Knowledge base, assets, escalations, audit-log browser, SLA/config editors are placeholders.
- LDAP runs over plain `ldap://…:389`; use `ldaps://` in production.
- Credentials still have fallbacks in `application.yml` and are in git history — rotate and move to env vars.
- Several tables were rebuilt outside Flyway on 2026-09-18 with VARCHAR instead of NVARCHAR; non-Latin
  text is not stored correctly there. Fix with a migration when convenient.
- The two tests noted in [§2](#2-build-run-test) contradict the current auto-create login.
