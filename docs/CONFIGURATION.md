# Configuration & versions

## 1. Versions in use

| Thing | Version | Notes |
|---|---|---|
| **App** (`itsm-portal`) | `0.8.0-SNAPSHOT` | `pom.xml` |
| **Java** | **8** (`java.version` 1.8) | compile & runtime target |
| **Spring Boot** | **2.7.18** | parent `spring-boot-starter-parent`; brings Spring Framework 5.3, Spring Security 5.7, Thymeleaf 3.0 |
| **Packaging** | WAR | deployed to standalone Tomcat |
| **Servlet container (runtime)** | **Tomcat 9** | standalone on the server; embedded Tomcat is used only for local runs |
| **Hibernate / JPA** | Spring Boot 2.7 managed (Hibernate 5.6) | dialect `SQLServer2012Dialect`, `ddl-auto: validate` |
| **SQL Server JDBC** | `mssql-jdbc` **11.2.3.jre8** | |
| **Flyway** | 9.22.3 (+ `flyway-sqlserver`) | **disabled in prod** (SQL Server 2012 needs Flyway Teams); see §5 |
| **LDAP** | `spring-ldap-core` / `spring-security-ldap` (Boot-managed) | login via a custom JNDI client, `itsm.ldap.*` |
| **UnboundID LDAP SDK** | 6.0.11 | in-memory AD for tests only |
| **H2** | Boot-managed | test / local preview profile only — never prod |
| Starters | web, security, data-jpa, thymeleaf, validation, mail, actuator | |

> `/actuator/info` deliberately exposes **only the app name**, not Java/Spring versions, so an attacker can't tell
> what to target.

## 2. Where it runs

| | Production | Local / embedded |
|---|---|---|
| Container | standalone Tomcat 9 (on `D:`) | embedded Tomcat (`mvn spring-boot:run`) |
| Port | **8090** (`conf/server.xml`) | **8092** (`server.port` default) |
| Context path | `/itsm-portal` (WAR name) | `/` |
| URL | `http://<server>:8090/itsm-portal` | `http://localhost:8092` |

## 3. Config files & profiles

| File | Role |
|---|---|
| `src/main/resources/application.yml` | base config (below) |
| `application-h2.yml` | local preview on H2 — **`itsm.license.enforce: false`** |
| `application-test.yml` | test profile — H2, jobs off, **`itsm.license.enforce: false`** |
| `D:\itsm-config\itsm-secrets.yml` | **external secrets**, imported via `spring.config.import`; set `ITSM_CONFIG_DIR` to move the folder. Template: `config-template/itsm-secrets.yml`. Passwords are **never** in the repo. |

Change a password → edit `itsm-secrets.yml` and restart Tomcat.

## 4. Environment variables / overrides (from `application.yml`)

| Variable | Default | Purpose |
|---|---|---|
| `ITSM_CONFIG_DIR` | `D:/itsm-config` | external secrets + **license file** folder |
| `DB_URL` | `jdbc:sqlserver://10.65.7.245:1865;databaseName=ITSM_PROD;encrypt=true;trustServerCertificate=true` | datasource |
| `DB_USERNAME`, `DB_PASSWORD` | — (from secrets) | datasource credentials |
| `HIKARI_MAX_POOL` / `HIKARI_MIN_IDLE` | 30 / 10 | connection pool (sized for 1000+ users) |
| `FLYWAY_ENABLED` | `false` | turn Flyway on only on SQL Server 2016+ |
| `MAIL_HOST` / `MAIL_PORT` | `10.65.8.64` / `25` | SMTP relay (no auth, no TLS) |
| `MAIL_ENABLED` | `true` | send ticket emails |
| `MAIL_FROM` / `MAIL_FROM_NAME` | `no-reply@authum.com` / **`IT Service Desk`** | sender address / display name |
| `ITSM_PORTAL_URL` | *(blank)* | link put into emails |
| `LDAP_URL` | `ldap://AUTHPDC.Authum.local:389` | AD |
| `LDAP_BASE_DN` / `LDAP_BIND_DN` / `LDAP_BIND_PASSWORD` | `dc=Authum,dc=local` / `70902907@Authum.local` / *(secret)* | AD bind |
| `SERVER_PORT` | `8092` | embedded port |
| `SESSION_TIMEOUT` / `SESSION_ABSOLUTE_TIMEOUT` | `30m` / `8h` | idle / absolute session limits |
| `SESSION_COOKIE_SECURE` | `false` | set `true` behind HTTPS |
| `FORWARD_HEADERS_STRATEGY` | `none` | `framework` only behind a trusted reverse proxy |
| `ITSM_BOOTSTRAP_ADMINS` | *(blank)* | AD username(s)/employee no(s) made System Administrator on a fresh DB, **only while nobody holds that role** |
| `itsm.license.enforce` | `true` | license enforcement (false in test/h2) — see [LICENSING.md](LICENSING.md) |

## 5. Schema install (no Flyway in prod)

Production is **SQL Server 2012**, which Flyway 9 Community refuses, so Flyway is **off** (`FLYWAY_ENABLED=false`).
Instead:
- The DBA installs the schema once from `db/install/install-itsm-portal.sql`.
- On every app start, **`SchemaInstaller`** runs the idempotent upgrade scripts `db/install/upgrades/U*.sql`
  (currently up to `U15`).
- The matching `db/migration/V*.sql` files are kept as the authoritative shape (and are what Flyway would run on
  SQL Server 2016+ if enabled). Latest is `V15` / `U15` (SLA tracking); the next would be `V16`/`U16`.
- JPA runs with `ddl-auto: validate` — the app checks the schema matches the entities but never alters it.

## 6. Background jobs

Enabled by `itsm.jobs.enabled` (default **true**; **false** in tests). `@EnableScheduling` via
`config/SchedulingConfig`.

| Job | Class | Cadence (default) | Settings |
|---|---|---|---|
| SLA monitor / alerts | `SlaMonitorService` | every **5 min** (`itsm.jobs.sla-interval-ms` 300000; initial delay `…sla-initial-delay-ms` 90000) | `sla.alerts`, `sla.near-percent` |
| Confirmation auto-close | `ConfirmationAutoCloseJob` | every **15 min** (`itsm.jobs.auto-close-interval-ms` 900000; initial delay `…auto-close-initial-delay-ms` 120000) | `workflow.confirmation-hours`, `workflow.reopen-hours`, `workflow.requester-confirmation` |

## 7. Editable settings — `system_setting` table (Admin → System Configuration)

| Key | Default | Meaning |
|---|---|---|
| `workflow.requester-confirmation` | `true` | ask the requester to confirm before closing |
| `workflow.confirmation-hours` | `48` | hours a resolved ticket waits before auto-close (2 days) |
| `workflow.reopen-hours` | `48` | hours the requester may re-open after an auto-close |
| `workflow.max-manager-hops` | `12` | cap on the manager-approval chain length |
| `approval.remarks-min-length` | `10` | minimum characters for mandatory remarks |
| `sla.near-percent` | `20` | % of the SLA window left that flips a ticket to NEAR |
| `sla.alerts` | `true` | send NEAR/BREACHED alert emails |
| `license.last-seen-utc` | *(system)* | clock-tamper guard for licensing — **not** user-editable |

Defaults come from code (`WorkflowEngine`, `SlaService`); a row in `system_setting` overrides the default.

## 8. Security posture (quick reference)

- Login throttling: `LoginAttemptService` (5 per user / 20 per IP per 15 min), fail-closed LDAP provider.
- Session cookies: `HttpOnly`; set `SESSION_COOKIE_SECURE=true` behind HTTPS; 30 min idle / 8 h absolute.
- Actuator: only `health` + `info`, no details, no versions.
- Audit: every ticket action and login failure recorded (`audit_log`).
- Licensing: public key **compiled into the WAR** (`LicenseKeys`), not a swappable file — see [LICENSING.md](LICENSING.md).

See [ENDPOINTS.md](ENDPOINTS.md) for the per-route authorities.
