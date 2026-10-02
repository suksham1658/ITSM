# Database map — what lives where

Database: **SQL Server**, schema `dbo`, database `ITSM_PROD` in production. All text columns are `NVARCHAR`/`NCHAR`
(Unicode). The schema is installed by the DBA from `db/install/install-itsm-portal.sql` (Flyway is **off** in prod —
see [CONFIGURATION.md](CONFIGURATION.md)); the matching `db/migration/V*.sql` files are the authoritative source of
each table's shape, and `db/install/upgrades/U*.sql` are the idempotent upgrade scripts run by `SchemaInstaller`.

JPA is `ddl-auto: validate` — the app never changes the schema, it only checks it matches the entities.

---

## "I need to find X — which table?"

| Looking for… | Table(s) |
|---|---|
| A ticket and its current state | `ticket` (status in `status_code`, current step label in `progress_code`) |
| A ticket's public number (ITSM-2026-00xxxx) | `ticket.public_number`; numbering config in `ticket_number_config` |
| The approval/fulfilment steps a ticket went through | `workflow_instance` (1/ticket) → `workflow_instance_stage` (the steps) |
| Who was assigned / reassigned to whom | `ticket_assignment_log` (from_employee, to_employee, by_employee, remarks) |
| Comments on a ticket | `ticket_comment` (+ `comment_attachment` for files on a comment) |
| File attachments on a ticket | `ticket_attachment`; allowed types/size in `attachment_policy` |
| SLA clock for a ticket (due times, met/breached, pauses) | `ticket_sla`; the targets per priority in `sla_policy` |
| Working hours & holidays used by SLA | `business_calendar`, `holiday` |
| A person (from AD) and their reporting line | `employee` (manager_id, hod_id, delegate_id, portal_active, phone, office…) |
| A person's roles | `employee_role` (employee ↔ role); role perms in `role` / `permission` / `role_permission` |
| Assignment groups (Service Desk, Implementors, Security Team) and members | `assignment_group`, `assignment_group_member` |
| The ticket catalog (type → category → sub-category) | `ticket_type`, `category`, `sub_category`; category→implementor in `category_implementor` |
| Which implementors handle a category | `category_implementor` |
| Workflow templates / rules | `workflow_definition`, `workflow_stage`, `workflow_stage_transition`, `workflow_rule` |
| Editable settings (confirmation hours, SLA near %, etc.) | `system_setting` |
| The audit trail (every action) | `audit_log` |
| Maker-checker master-data change requests | `config_change_request` |
| In-app notifications (bell) | `notification`; email templates in `email_template`; rules in `notification_rule` |
| Knowledge base | `kb_category`, `kb_article`, `kb_article_feedback`, `kb_article_ticket` |
| Assets | `asset` |
| Escalation config/history | `escalation_matrix`, `escalation_event` |
| LDAP/AD sync history | `ldap_sync_log` |
| The installed license timestamp (clock-tamper guard) | `system_setting` key `license.last-seen-utc` (the `.lic` file itself lives on disk, not in the DB) |

---

## Entity → table map (`com.nbfc.itsm.domain`)

| Entity class | Table |
|---|---|
| `Ticket` | `ticket` |
| `TicketSla` | `ticket_sla` |
| `TicketComment` | `ticket_comment` |
| `TicketAttachment` | `ticket_attachment` |
| `TicketAssignmentLog` | `ticket_assignment_log` |
| `TicketType` | `ticket_type` |
| `Category` | `category` |
| `SubCategory` | `sub_category` |
| `TicketNumberConfig` | `ticket_number_config` |
| `AttachmentPolicy` | `attachment_policy` |
| `Employee` | `employee` |
| `EmployeeRoleAssignment` | `employee_role` |
| `Role` | `role` |
| `Permission` | `permission` |
| `Department` | `department` |
| `AssignmentGroup` | `assignment_group` |
| `AssignmentGroupMember` | `assignment_group_member` |
| `WorkflowDefinition` | `workflow_definition` |
| `WorkflowStage` | `workflow_stage` |
| `WorkflowStageTransition` | `workflow_stage_transition` |
| `WorkflowRule` | `workflow_rule` |
| `WorkflowInstance` | `workflow_instance` |
| `WorkflowInstanceStage` | `workflow_instance_stage` |
| `SlaPolicy` | `sla_policy` |
| `BusinessCalendar` | `business_calendar` |
| `Holiday` | `holiday` |
| `SystemSetting` | `system_setting` |
| `AuditLog` | `audit_log` |
| `ConfigChangeRequest` | `config_change_request` |
| `Notification` | `notification` |

Tables **without** a JPA entity (managed by SQL/other code): `category_implementor`, `comment_attachment`,
`email_template`, `notification_rule`, `escalation_matrix`, `escalation_event`, `ldap_sync_log`, `role_permission`,
`workflow_instance_stage_assignee`, `kb_category`, `kb_article`, `kb_article_feedback`, `kb_article_ticket`,
`asset`, `ticket_relation`.

---

## Central table: `ticket` (key columns)

```
ticket_id (PK)            public_number ("ITSM-2026-00xxxx")
ticket_type_id            category_id            sub_category_id
subject                   description
priority_code             impact_code            urgency_code
confidentiality_code      serial_number          location  application_name
asset_id                  required_date
requester_id              department_id
status_code               progress_code          reject_reason
assigned_group_id         assigned_implementor_id
workflow_instance_id      (+ created/updated audit columns from BaseAuditableEntity)
```
- `status_code` values: see [WORKFLOWS.md](WORKFLOWS.md) §1.
- `serial_number` is the Hardware-category field (added in V13/U13); it replaced the old confidentiality field on
  the raise form.
- `assigned_implementor_id` is the single current owner once one implementor picks the work up.

## `ticket_sla` (key columns)
```
ticket_sla_id (PK)   ticket_id   sla_policy_id   state_code (WITHIN/NEAR/BREACHED/MET)
sla_start_utc        response_due_utc   resolve_due_utc
first_response_utc   resolved_utc       response_breached
paused  paused_at_utc  paused_minutes
near_alerted_utc     breach_alerted_utc
```
All times are stored in **UTC**; business-time maths is done in IST (see [SLA.md](SLA.md)).

---

## Full table inventory (44)

```
asset                         assignment_group              assignment_group_member
attachment_policy             audit_log                     business_calendar
category                      category_implementor          comment_attachment
config_change_request         department                    email_template
employee                      employee_role                 escalation_event
escalation_matrix             holiday                        kb_article
kb_article_feedback           kb_article_ticket             kb_category
ldap_sync_log                 notification                  notification_rule
permission                    role                          role_permission
sla_policy                    sub_category                  system_setting
ticket                        ticket_assignment_log         ticket_attachment
ticket_comment                ticket_number_config          ticket_relation
ticket_sla                    ticket_type                   workflow_definition
workflow_instance             workflow_instance_stage       workflow_instance_stage_assignee
workflow_rule                 workflow_stage                workflow_stage_transition
```

> Tip for debugging a single ticket end-to-end:
> `ticket` → `workflow_instance` (by `workflow_instance_id`) → `workflow_instance_stage` (ordered by `stage_order`)
> → `ticket_assignment_log` + `ticket_comment` + `ticket_sla` + `audit_log` (filter by ticket id).
