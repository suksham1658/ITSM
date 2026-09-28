# ITSM Portal — Database tables

Every table in `ItsmPortal` (schema `dbo`), what it stores, what each column means, which part of the
application writes it, and queries to look things up. Definitions come from Flyway `V1__core_tables.sql`
(later migrations: V7 rebuilds `workflow_rule`, V8 fixes `notification` text types).

Conventions: `*_id` = primary key (`BIGINT IDENTITY`); `*_utc` = UTC time (`DATETIME2(3)`); `is_*` = 0/1;
**?** = column may be NULL. Codes shown in *italics* are enforced by CHECK constraints.

Quick map:

| Area | Tables |
|---|---|
| People & access | `employee`, `department`, `role`, `permission`, `role_permission`, `employee_role`, `assignment_group`, `assignment_group_member`, `ldap_sync_log` |
| Ticket catalog & settings | `ticket_type`, `category`, `sub_category`, `sla_policy`, `ticket_number_config`, `attachment_policy`, `business_calendar`, `holiday`, `system_setting` |
| Workflow design | `workflow_definition`, `workflow_stage`, `workflow_stage_transition`, `workflow_rule` |
| Tickets & their progress | `ticket`, `workflow_instance`, `workflow_instance_stage`, `ticket_sla`, `ticket_comment`, `ticket_attachment`, `comment_attachment`, `ticket_relation` |
| Notifications | `notification`, `notification_rule`, `email_template` |
| Governance | `config_change_request`, `audit_log` |
| Present but not used by the code yet | `asset`, `escalation_matrix`, `escalation_event`, `kb_category`, `kb_article`, `kb_article_feedback`, `kb_article_ticket` |
| Flyway | `flyway_schema_history` |

---

## 1. People & access

### `employee` — every portal user (and managers synced from AD)
Written by: login (`PortalUserService.loadActivePrincipal` creates/updates from AD; `syncHierarchy` sets
manager/department), Admin → Users (`EmployeeSetupService`, `AdminUserService` for `portal_active`).

| Column | Meaning |
|---|---|
| employee_id | Internal id used by every other table |
| employee_no | AD `employeeID` (else `employeeNumber`, else sAMAccountName) — unique, e.g. `70311158` |
| upn? | AD userPrincipalName / e-mail |
| sam_account_name? | AD login name (what users type at login) |
| display_name | AD displayName (e.g. `Pankaj Kanhaiya/Authum/IT`) |
| designation? | AD `title` (display only) |
| email? | AD `mail` |
| department_id? | → `department`; from AD `department` if it matches a department name/code, or set by admin |
| manager_id? | → `employee`; **immediate manager** from AD `manager` — first approver of Service Requests |
| hod_id? | → `employee`; optional explicit HOD where the approval chain stops |
| portal_active | 1 = may log in and act; 0 = blocked (`/login?error=denied`) |
| last_ldap_sync_utc? | Last login / directory refresh |
| notes? | Free text (unused by UI) |
| created_at_utc, updated_at_utc | Row audit times |

### `department`
`department_id`, `code` (e.g. IT, FIN), `name` (e.g. Information Technology), `is_active`, `created_at_utc`.
Seeded by V4. Matched against AD `department` at login.

### `role` — portal roles
`role_id`, `code` (EMPLOYEE, MANAGER, HOD, CISO, IT_SERVICE_DESK, IT_IMPLEMENTOR, IT_ADMIN,
SYSTEM_ADMINISTRATOR, or custom), `name`, `is_system` (1 = seeded, cannot be deleted), `is_active`,
`created_at_utc`. Written by V4 and Admin → Roles & Permissions (`RoleAdminService.apply/delete`).

### `permission` — what can be done
`permission_id`, `code` (TICKET_CREATE, TICKET_APPROVE_ASSIGNED_STAGE, ADMIN_USER_MANAGE, …), `description`.
Seeded by V4; not edited by the UI.

### `role_permission` — which permissions each role has
`role_id` → `role`, `permission_id` → `permission`. Written by V4 and approved role edits.

### `employee_role` — **who has which role**
| Column | Meaning |
|---|---|
| employee_id | → `employee` |
| role_id | → `role` |
| assigned_at_utc | When granted |
| assigned_by_id? | → `employee` who approved/applied it |
Written by Admin → Users (`AdminUserService.apply` / direct by System Administrator) and the
`ITSM_BOOTSTRAP_ADMINS` bootstrap.

### `assignment_group` — work queues
`assignment_group_id`, `code` (IT_SERVICE_DESK, IT_IMPLEMENTORS, IT_SECURITY_TEAM), `name`, `is_active`. Seeded by V4.

### `assignment_group_member` — people added to a group by hand
`assignment_group_id` → `assignment_group`, `employee_id` → `employee`. Written by Admin → Users → Assignment groups.
Note: holders of the IT_SERVICE_DESK / IT_IMPLEMENTOR **roles** also count as members without a row here.

### `ldap_sync_log`
`ldap_sync_log_id`, `started_at_utc`, `finished_at_utc?`, `result_code`, `inserted_count`, `updated_count`,
`message?`. Reserved for a bulk directory sync; login-time sync does not write it.

---

## 2. Ticket catalog & settings

### `ticket_type`
`ticket_type_id`, `code` (INCIDENT, SERVICE_REQUEST, ACCESS_REQUEST, CHANGE_REQUEST, SECURITY_INCIDENT,
PROBLEM, HARDWARE_REQUEST, SOFTWARE_REQUEST), `name`, `sort_order`, `is_active`.

### `category`, `sub_category`
`category`: `category_id`, `code`, `name`, `sort_order`, `is_active`.
`sub_category`: `sub_category_id`, `category_id` → `category`, `code`, `name`, `sort_order`, `is_active`.
Shown on Raise Request; `category.name` / `sub_category.name` are also used by workflow rules.

### `sla_policy` — response/resolution targets per priority
`sla_policy_id`, `priority_code` (*Critical, High, Medium, Low*), `response_minutes`, `resolution_minutes`, `is_active`.

### `ticket_number_config` — public ticket numbers
`prefix` (ITSM), `include_year`, `padding` (*4–10*), `sequence_year`, `last_allocated` (last number used).
Written by `TicketNumberService.allocate` on every submit.

### `attachment_policy`
`max_bytes`, `allowed_extensions`, `allowed_mime_types`, `virus_scan_required`. Read by `AttachmentService`.

### `business_calendar`, `holiday` — SLA working time
`business_calendar`: `weekday_iso` (*1–7*), `start_time`, `end_time`, `timezone_id`, `is_working_day`.
`holiday`: `holiday_date`, `name`, `is_national`.

### `system_setting`
`setting_key` (PK), `setting_value`, `category`, `description?`, `is_secret` (*must be 0*).
Keys: `workflow.max-manager-hops` (12), `approval.remarks-min-length` (10), `sla.amber-percent` (20), `ui.timezone`.

---

## 3. Workflow design (templates)

### `workflow_definition` — a workflow template
`workflow_definition_id`, `code` (SR_CHAIN_TO_HOD_CISO_IMPL, SR_CHAIN_TO_HOD_IMPL, INCIDENT_SD_THEN_IMPL,
INCIDENT_DIRECT_IMPL, SECURITY, PRIVILEGED_ACCESS), `name`, `version_no`,
`status_code` (*Draft, PendingChecker, Active, Retired, Inactive*), `description?`, `created_at_utc`.

### `workflow_stage` — steps of a template
| Column | Meaning |
|---|---|
| workflow_definition_id | → template |
| stage_order | 10, 20, 30… |
| code, label | e.g. `hierarchy` / "Reporting hierarchy through HOD" |
| stage_type | *APPROVAL, ASSIGNMENT, FULFILMENT, CONFIRMATION, CLOSURE* |
| actor_strategy | who acts: DYNAMIC_HIERARCHY_TO_HOD (manager chain), NAMED_ROLE, SERVICE_DESK, ASSIGNMENT_GROUP, IMPLEMENTOR, REQUESTER, SYSTEM, LDAP_MANAGER, LDAP_HOD |
| role_id? | → `role` for NAMED_ROLE (e.g. CISO) |
| assignment_group_id? | → `assignment_group` for desk / implementor / security steps |
| send_back_target? | *PREVIOUS_STAGE* or *REQUESTER* |

### `workflow_stage_transition` — allowed actions per step
`workflow_stage_id` → `workflow_stage`, `action_code` (APPROVE, REJECT, SEND_BACK, ASSIGN, REASSIGN,
ACCEPT, START, HOLD, RESOLVE, COMPLETE), `remarks_required`.

### `workflow_rule` — which template a ticket gets
| Column | Meaning |
|---|---|
| name | Label shown in Workflow Config |
| priority | Lower wins (10, 20, 21, 22, 30, 40, 999); unique among Active rules |
| status_code | *Active, Inactive, PendingChecker* |
| condition_json | e.g. `{"ticket_type":["Incident"]}`; `{}` = matches everything |
| workflow_definition_id | → template to use |
Empty table ⇒ every submit fails ("no approval workflow is set up").

---

## 4. Tickets and their progress

### `ticket`
| Column | Meaning |
|---|---|
| public_number | `ITSM-2026-001201` (drafts: `DRAFT-…`) |
| ticket_type_id, category_id, sub_category_id | → catalog |
| subject, description | Entered by requester |
| priority_code, urgency_code | *Critical, High, Medium, Low* |
| impact_code | *Individual, Department, Multiple Departments, Organization-wide* |
| confidentiality_code | *Normal, Confidential, Highly Confidential* |
| location?, application_name?, required_date?, asset_id? | Optional details |
| requester_id | → `employee` who raised it |
| department_id? | Requester's department at submit time |
| status_code | *Draft, Pending Approval, Approved, Assigned, In Progress, On Hold, Resolved, Closed, Rejected* |
| progress_code? | Label of the current step |
| assigned_group_id? | → `assignment_group` handling it |
| assigned_implementor_id? | → `employee` doing the work |
| workflow_instance_id? | → `workflow_instance` (NULL for drafts) |
| major_incident | 0/1 |
| reject_reason? | Remarks when declined |
| created_at_utc, updated_at_utc | Times |

### `workflow_instance` — the running workflow of one ticket
`ticket_id`, `workflow_definition_id` (template used), `workflow_rule_id?` (rule that matched),
`current_stage_id?` (→ `workflow_instance_stage`), `status_code` (InProgress, Completed, Rejected), `created_at_utc`.

### `workflow_instance_stage` — **every step of one ticket and who acts** (most useful for "where is my ticket?")
| Column | Meaning |
|---|---|
| workflow_instance_id | → `workflow_instance` |
| workflow_stage_id? | → template step |
| stage_order, code, label | Order and name (manager steps are labelled with the manager's name) |
| stage_type, actor_strategy | As in `workflow_stage` |
| resolved_employee_id? | → the **person** who must act (manager, implementor, requester) |
| resolved_role_id? | → role whose holders act (e.g. CISO) |
| resolved_group_id? | → group whose members act (Service Desk, Implementors) |
| status_code | *Pending, Current, Completed, Rejected, Skipped* — exactly one **Current** while open |
| action_code?, remarks?, acted_at_utc? | What was done, remarks, when |
Trigger `trg_wfis_mandatory_remarks` (V2) blocks APPROVE/REJECT/SEND_BACK without remarks.

### `ticket_sla`
`ticket_id`, `sla_policy_id`, `sla_start_utc`, `response_due_utc`, `resolve_due_utc`, `first_response_utc?`,
`resolved_utc?`, `paused`, `state_code` (*WITHIN, NEAR, BREACHED, MET*). Written by `SlaService`.

### `ticket_comment`
`ticket_id`, `author_id` → `employee`, `body`, `is_internal` (1 = staff-only note), `created_at_utc`.

### `ticket_attachment`, `comment_attachment`
`original_name`, `storage_key` (file path under `FILE_STORAGE_ROOT`), `content_type?`, `byte_length`,
`uploaded_by_id` → `employee`, `uploaded_at_utc`; linked to `ticket_id` / `ticket_comment_id`.
`comment_attachment` is not used by the UI yet.

### `ticket_relation`
`ticket_id`, `related_ticket_id`, `relation_code` (default RELATED). Not used by the UI yet.

---

## 5. Notifications

### `notification` — bell / Notifications page
| Column | Meaning |
|---|---|
| recipient_id | → `employee` who sees it |
| ticket_id? | → `ticket` it is about (click opens the ticket) |
| title | e.g. "Approval needed: ITSM-2026-001205" |
| body | Details (who, remarks, where it is now) |
| is_read | 0 = unread (counted on the bell), 1 = read |
| created_at_utc | When sent |
Written by `NotificationService` inside the same transaction as the workflow action.

### `notification_rule`
`event_code` (TICKET_CREATED, APPROVAL_REQUIRED, APPROVED, REJECTED, ASSIGNED, RESOLVED, SLA_NEAR,
SLA_BREACHED), `in_app` (0 turns that in-app notification off), `send_email` (not used yet), `is_active`.

### `email_template`
`code`, `subject`, `body`. Reserved for e-mail; not sent yet.

---

## 6. Governance

### `config_change_request` — maker-checker queue
| Column | Meaning |
|---|---|
| change_type | USER_ROLE_ASSIGN, USER_ROLE_REMOVE, USER_PORTAL_ACTIVE, ROLE_DEFINITION_CREATE, ROLE_DEFINITION_UPDATE |
| entity_name, entity_key? | What it changes (e.g. `employee_role`, `8:3` = employee 8, role 3) |
| payload_json, previous_json? | New and old values |
| description | Human text |
| status_code | *PendingApproval, Applied, Rejected* |
| requested_by_id, requested_at_utc | Maker |
| reviewed_by_id?, reviewed_at_utc?, reject_reason? | Checker (same as maker for System Administrator direct changes) |

### `audit_log` — append-only trail (trigger `trg_audit_log_immutable` blocks UPDATE/DELETE)
| Column | Meaning |
|---|---|
| occurred_at_utc | When |
| employee_id?, employee_no?, role_code? | Who (and active role) |
| module_code | AUTH, ADMIN, TICKET, SYSTEM |
| action_code | LOGIN, LOGOUT, ROLE_SWITCH, PROPOSE, APPROVE, REJECT, ROLE_ASSIGN, ROLE_REMOVE, ROLE_CREATE, ROLE_UPDATE, ROLE_DELETE, REPORTING_LINE, GROUPS, BOOTSTRAP_ADMIN, CREATE, WORKFLOW_START, ASSIGN, START, RESOLVE, COMMENT, UNHANDLED_ERROR, … |
| ticket_id? | Ticket concerned |
| old_value?, new_value? | Before / after or detail text |
| ip_address?, user_agent? | Client |
| result_code | SUCCESS / FAILURE |

---

## 7. Present but unused by the code

`asset` (asset register), `escalation_matrix` / `escalation_event` (SLA escalations — seeded rows only),
`kb_category` / `kb_article` / `kb_article_feedback` / `kb_article_ticket` (knowledge base).

`flyway_schema_history` — one row per applied migration (V1–V8 + callbacks); never edit it by hand.

---

## 8. Handy queries

```sql
-- A user: roles, manager, department, groups
SELECT e.employee_id, e.employee_no, e.sam_account_name, e.display_name, e.portal_active,
       m.display_name AS manager, d.name AS department,
       STUFF((SELECT ', ' + r.code FROM dbo.employee_role er JOIN dbo.role r ON r.role_id = er.role_id
              WHERE er.employee_id = e.employee_id FOR XML PATH('')), 1, 2, '') AS roles,
       STUFF((SELECT ', ' + g.code FROM dbo.assignment_group_member gm JOIN dbo.assignment_group g ON g.assignment_group_id = gm.assignment_group_id
              WHERE gm.employee_id = e.employee_id FOR XML PATH('')), 1, 2, '') AS groups_added_by_hand
FROM dbo.employee e
LEFT JOIN dbo.employee m ON m.employee_id = e.manager_id
LEFT JOIN dbo.department d ON d.department_id = e.department_id
WHERE e.employee_no = '70311158';

-- Reporting line upwards from a user (approval chain)
WITH chain AS (
  SELECT employee_id, display_name, manager_id, 0 AS lvl FROM dbo.employee WHERE employee_no = '50058036'
  UNION ALL
  SELECT m.employee_id, m.display_name, m.manager_id, c.lvl + 1 FROM dbo.employee m JOIN chain c ON m.employee_id = c.manager_id WHERE c.lvl < 15)
SELECT lvl, display_name FROM chain ORDER BY lvl;

-- Everyone who holds a role
SELECT r.code, e.employee_no, e.display_name, e.portal_active
FROM dbo.employee_role er JOIN dbo.role r ON r.role_id = er.role_id JOIN dbo.employee e ON e.employee_id = er.employee_id
ORDER BY r.code, e.display_name;

-- A ticket: header + every step and who must act (Current = waiting now)
SELECT t.public_number, t.status_code, tt.name AS type, req.display_name AS requester,
       s.stage_order, s.label, s.stage_type, s.status_code AS step_status,
       p.display_name AS person, ro.code AS role, g.code AS grp, s.action_code, s.remarks, s.acted_at_utc
FROM dbo.ticket t
JOIN dbo.ticket_type tt ON tt.ticket_type_id = t.ticket_type_id
JOIN dbo.employee req ON req.employee_id = t.requester_id
JOIN dbo.workflow_instance wi ON wi.ticket_id = t.ticket_id
JOIN dbo.workflow_instance_stage s ON s.workflow_instance_id = wi.workflow_instance_id
LEFT JOIN dbo.employee p ON p.employee_id = s.resolved_employee_id
LEFT JOIN dbo.role ro ON ro.role_id = s.resolved_role_id
LEFT JOIN dbo.assignment_group g ON g.assignment_group_id = s.resolved_group_id
WHERE t.public_number = 'ITSM-2026-001201'
ORDER BY s.stage_order;

-- All open tickets and where each one is waiting
SELECT t.public_number, t.status_code, s.label AS waiting_at,
       COALESCE(p.display_name, g.name, ro.name) AS waiting_for
FROM dbo.ticket t
JOIN dbo.workflow_instance_stage s ON s.workflow_instance_id = t.workflow_instance_id AND s.status_code = 'Current'
LEFT JOIN dbo.employee p ON p.employee_id = s.resolved_employee_id
LEFT JOIN dbo.assignment_group g ON g.assignment_group_id = s.resolved_group_id
LEFT JOIN dbo.role ro ON ro.role_id = s.resolved_role_id
ORDER BY t.ticket_id DESC;

-- Workflow rules in the order they are tried
SELECT r.priority, r.status_code, r.name, r.condition_json, d.code AS workflow, d.status_code AS wf_status
FROM dbo.workflow_rule r JOIN dbo.workflow_definition d ON d.workflow_definition_id = r.workflow_definition_id
ORDER BY r.priority;

-- Notifications of a user (newest first)
SELECT n.created_at_utc, n.is_read, n.title, n.body
FROM dbo.notification n JOIN dbo.employee e ON e.employee_id = n.recipient_id
WHERE e.employee_no = '70311158' ORDER BY n.notification_id DESC;

-- Pending maker-checker requests
SELECT c.config_change_request_id, c.change_type, c.description, e.display_name AS requested_by, c.requested_at_utc
FROM dbo.config_change_request c JOIN dbo.employee e ON e.employee_id = c.requested_by_id
WHERE c.status_code = 'PendingApproval' ORDER BY c.requested_at_utc;

-- Audit trail of a user (latest 100)
SELECT TOP 100 occurred_at_utc, module_code, action_code, result_code, ticket_id, new_value
FROM dbo.audit_log WHERE employee_no = '70311158' ORDER BY audit_log_id DESC;

-- Login failures today
SELECT occurred_at_utc, new_value, ip_address FROM dbo.audit_log
WHERE module_code = 'AUTH' AND action_code = 'LOGIN' AND result_code = 'FAILURE'
  AND occurred_at_utc >= CAST(SYSUTCDATETIME() AS DATE) ORDER BY audit_log_id DESC;

-- SLA state of open tickets
SELECT t.public_number, t.priority_code, s.state_code, s.response_due_utc, s.resolve_due_utc, s.paused
FROM dbo.ticket_sla s JOIN dbo.ticket t ON t.ticket_id = s.ticket_id
WHERE t.status_code NOT IN ('Closed', 'Rejected') ORDER BY s.resolve_due_utc;

-- Applied migrations
SELECT installed_rank, version, description, success, installed_on FROM dbo.flyway_schema_history ORDER BY installed_rank;
```

All times are stored in **UTC**; add 5:30 for IST (e.g. `DATEADD(MINUTE, 330, created_at_utc)`).
