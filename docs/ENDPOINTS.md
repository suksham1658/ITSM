# HTTP endpoints (for debugging)

Every route the app serves, by controller. **Paths are relative to the context root** — prefix with
`http://<server>:8090/itsm-portal` in production (or `http://localhost:8092` for a local embedded run).

Most pages are server-rendered Thymeleaf (`GET` returns HTML); `POST`s are form submits that redirect back.
CSRF protection is on for all state-changing POSTs. Access rules are from
`security/SecurityConfig.java` — the guarding **authority** is shown per group. Anything not explicitly permitted
requires an authenticated session (`anyRequest().authenticated()`).

## Public (no login) — `permitAll`
| Method | Path | Purpose |
|---|---|---|
| GET | `/login` | login page (`LoginController`) |
| GET | `/license-unavailable` | shown when the license blocks non-admins (`LicenseController`) |
| GET | `/error`, `/403` | error / access-denied pages |
| GET | `/actuator/health`, `/actuator/info` | health & app name only (no versions) |
| — | `/css/**`, `/js/**`, `/images/**`, `/webfonts/**` | static assets (content-hashed, cached 1 year) |

## Portal pages — `PortalPageController` (authenticated; some need an authority)
| Method | Path | Purpose | Authority |
|---|---|---|---|
| GET | `/` | dashboard | authenticated |
| GET | `/approvals` | everything waiting for me to approve | `TICKET_APPROVE_ASSIGNED_STAGE` |
| GET | `/queue/desk` | IT Service Desk triage queue | `TICKET_VIEW_QUEUE_ALL` |
| GET | `/queue/assignment` | assignment queue | `TICKET_VIEW_QUEUE_ALL` |
| GET | `/queue/mine` | my implementation work | `TICKET_FULFIL` |
| GET | `/queue/work` | work queue | `TICKET_FULFIL` |
| GET | `/queue/implementation` | implementation queue | `TICKET_FULFIL` |
| GET | `/sla` | SLA dashboard | `SLA_MONITOR` |
| GET | `/escalations` | escalations | `SLA_MONITOR` |
| GET | `/risk` | risk view | `TICKET_VIEW_SECURITY` |
| GET | `/kb` | knowledge base | `KB_READ` |

## Tickets — `TicketController` (authenticated)
| Method | Path | Purpose | Authority |
|---|---|---|---|
| GET | `/tickets` | my tickets (all statuses I created) | authenticated |
| GET | `/tickets/handled` | tickets I'm/was an actor on (approved, assigned, resolved, routed to me) — any status incl. closed | authenticated |
| GET | `/tickets/team` | my team's tickets | `TICKET_VIEW_TEAM` |
| GET | `/tickets/department` | department tickets | `TICKET_VIEW_DEPARTMENT` |
| GET | `/tickets/security` | security tickets | `TICKET_VIEW_SECURITY` |
| GET | `/tickets/changes` | change tickets | `TICKET_FULFIL` |
| GET | `/tickets/raise` | raise-request form | `TICKET_CREATE` |
| POST | `/tickets/raise` | submit a new ticket | `TICKET_CREATE` |
| GET | `/tickets/{id}` | ticket detail + workflow timeline | authenticated (visibility checked in code) |
| POST | `/tickets/{id}/action` | approve / assign / resolve / hold / reassign … | checked per stage by the engine |
| POST | `/tickets/{id}/comments` | add a comment | authenticated |
| POST | `/tickets/{id}/attachments` | upload an attachment (type/size per `attachment_policy`; PDFs magic-byte checked) | authenticated |
| GET | `/tickets/{id}/attachments/{aid}` | download an attachment | authenticated |
| POST | `/tickets/{id}/priority` | change priority (recalculates SLA) | authenticated/authorised |
| POST | `/tickets/{id}/admin-reject` | System Administrator reject-any | `ROLE_SYSTEM_ADMINISTRATOR` (checked in engine) |
| GET | `/tickets/{id}/reopen` | re-open page (after auto-close) | requester |
| POST | `/tickets/{id}/reopen` | "Not resolved" → back to implementor | requester |
| POST | `/tickets/{id}/confirm-closed` | "Resolved" → stays closed | requester |

## Notifications — `NotificationController` (authenticated)
| Method | Path | Purpose |
|---|---|---|
| GET | `/notifications` | list my notifications |
| GET | `/notifications/{id}/open` | open one (marks read, redirects to the ticket) |
| POST | `/notifications/read-all` | mark all read |

## Role switching — `RoleSwitchController`
| Method | Path | Purpose |
|---|---|---|
| POST | `/session/active-role` | switch the active role for this session |

## AD account self-service — `AdAccountController` (authority `AD_ACCOUNT_UNLOCK`)
| Method | Path | Purpose |
|---|---|---|
| GET | `/ad-accounts` | search AD accounts |
| GET | `/ad-accounts/account` | one account's status |
| POST | `/ad-accounts/unlock` | unlock an AD account |

## Reports — `ReportController` (authority `REPORT_VIEW`)
| Method | Path | Purpose |
|---|---|---|
| GET | `/reports` | report list |
| GET | `/reports/{code}` | a report (HTML) |
| GET | `/reports/{code}.csv` | CSV export (formula-injection guarded) |
| GET | `/reports/{code}.xls` | Excel export |

## IMAC (Install / Move / Add / Change)
There is no separate IMAC controller — IMAC is a **ticket type** that rides the normal ticket endpoints
(`/tickets/raise`, `/tickets/{id}`, `/tickets/{id}/action`, …). Raise a ticket with type **IMAC** and a
sub-category of **Install / Move / Add / Change**; the `IMAC_FLOW` workflow routes it manager approval →
IT Service Desk → Implementor → requester confirmation → close. See [WORKFLOWS.md](WORKFLOWS.md).

## Audit — `AuditController` (authority `AUDIT_VIEW`)
| Method | Path | Purpose |
|---|---|---|
| GET | `/audit` | the audit log (filterable; wide table scrolls horizontally) |
| GET | `/audit/export.xls` | download the audit trail **matching the current filters** as Excel (.xls) |

---

# Admin endpoints

## Catalog & config — `AdminCatalogController` (base `/admin`)
Reads need `ADMIN_MASTERDATA_PROPOSE`; **catalog edits and SLA edits need `ROLE_SYSTEM_ADMINISTRATOR`**
(`/admin/catalog/**`, `/admin/sla/**`).
| Method | Path | Purpose |
|---|---|---|
| GET | `/admin/categories` | ticket type / category / sub-category admin |
| POST | `/admin/catalog/types` · `/admin/catalog/types/{id}/rename` · `/admin/catalog/types/{id}/delete` | ticket types CRUD |
| POST | `/admin/catalog/categories` · `…/{id}/rename` · `…/{id}/delete` | categories CRUD |
| POST | `/admin/catalog/sub-categories` · `…/{id}/rename` · `…/{id}/delete` | sub-categories CRUD |
| POST | `/admin/categories/{id}/implementors` | set which implementors handle a category |
| GET | `/admin/sla` | SLA admin page |
| POST | `/admin/sla/policies/{id}` | edit an SLA policy (response/resolution minutes) |
| POST | `/admin/sla/calendar` | edit business hours |
| POST | `/admin/sla/holidays` · `/admin/sla/holidays/{id}/delete` | holidays |
| GET | `/admin/config` | System Configuration page |
| POST | `/admin/config` | save settings (confirmation hours, SLA near %, alerts…) |

## Users — `AdminUserController` (base `/admin`, authority `ADMIN_USER_MANAGE`)
| Method | Path | Purpose |
|---|---|---|
| GET | `/admin/users` · `/admin/users/{id}` | list / one user |
| POST | `/admin/users/{id}/propose-active` · `/deactivate` | enable/disable portal access |
| POST | `/admin/users/{id}/basic` · `/delegate` · `/resync` · `/propose-role` · `/reporting-line` · `/groups` | edit user, set delegate, resync from AD, propose role, set manager/HOD, set groups |
| GET | `/admin/change-requests` | maker-checker queue |
| POST | `/admin/change-requests/{id}/approve` · `/reject` | approve/reject a change (authority `ADMIN_MASTERDATA_APPROVE`) |

## Roles — `AdminRoleController` (base `/admin/roles`, authority `ADMIN_USER_MANAGE`)
`GET /admin/roles`, `GET /admin/roles/{id}`, `GET /admin/roles/new`, `POST /admin/roles`,
`GET /admin/roles/{id}/edit`, `POST /admin/roles/{id}`, `GET|POST /admin/roles/{id}/delete`.

## Workflow — `AdminWorkflowController` (base `/admin/workflow`, authority `ADMIN_MASTERDATA_PROPOSE`)
`GET /admin/workflow`, `GET /admin/workflow/{id}`, `POST /admin/workflow/{id}/draft|details|publish|discard`,
`POST /admin/workflow/new`, stage edits `POST /admin/workflow/{id}/stages[/{stageId}[/delete|/move]]`,
rule edits `POST /admin/workflow/rules[/{ruleId}[/delete]]`.

## Locations — `LocationController` (authority `LOCATION_MANAGE`)
| Method | Path | Purpose |
|---|---|---|
| GET | `/admin/locations` | list locations |
| POST | `/admin/locations` | add a location (name, address, sort) |
| POST | `/admin/locations/{id}` | edit a location |
| POST | `/admin/locations/{id}/delete` | delete a location |
| GET | `/locations/{id}/info` | JSON {name, address, hostname} — used by the IMAC raise form (authority `TICKET_CREATE`) |

`LOCATION_MANAGE` is granted to the System Administrator by default and assignable to any role.
On the IMAC form, choosing a location fills the **Office Address** (from the location) and a
read-only **Hostname** `AUTH-<first 3 letters of location>-NNNNNNN` (single running sequence).

## License — `LicenseController` (authority `ROLE_SYSTEM_ADMINISTRATOR`)
| Method | Path | Purpose |
|---|---|---|
| GET | `/admin/license` | license status + upload form |
| POST | `/admin/license/upload` | install a renewed `.lic` (verified before saving) |

See [LICENSING.md](LICENSING.md).
