/* =====================================================================
   ITSM Portal - one-time schema install (tables, triggers, indexes, master data,
   DB roles, default workflow rules, AD unlock permission).

   Generated from src/main/resources/db/migration V1-V9 + afterMigrate.sql, in order.
   Use this where Flyway cannot run (SQL Server 2012/2014, e.g. production ITSM_PROD).
   Works on SQL Server 2012 and later.

   Run ONCE, in the EMPTY application database, as a login with db_owner:
     sqlcmd -S 10.65.7.245,1865 -d ITSM_PROD -U <login> -C -i install-itsm-portal.sql
   (or open it in SSMS with ITSM_PROD selected and Execute).

   Safety: stops (nothing runs) if the current database is a system database or
   already contains the ITSM tables.
   ===================================================================== */
SET NOCOUNT ON;
IF DB_NAME() IN (N'master', N'model', N'msdb', N'tempdb')
BEGIN
    RAISERROR(N'Wrong database: select the ITSM application database (e.g. ITSM_PROD) and run again. Nothing was changed.', 16, 1);
    SET NOEXEC ON;
END
ELSE IF OBJECT_ID(N'dbo.employee', N'U') IS NOT NULL
BEGIN
    RAISERROR(N'ITSM tables already exist in this database; the install script must only run on an empty database. Nothing was changed.', 16, 1);
    SET NOEXEC ON;
END
GO

/* ===================================================================== V1__core_tables.sql */
PRINT N'V1__core_tables';
GO
/*
  Flyway V1 — aligned with database/tables.sql (CREATE only; no DROP/USE).
  SQL Server 2016 / 2019. Compat 130. GO batch separator.
*/
SET NOCOUNT ON;
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
GO

CREATE TABLE dbo.department (
    department_id     BIGINT IDENTITY(1,1) NOT NULL,
    code              NVARCHAR(32) NOT NULL,
    name              NVARCHAR(128) NOT NULL,
    is_active         BIT NOT NULL CONSTRAINT DF_department_active DEFAULT (1),
    created_at_utc    DATETIME2(3) NOT NULL CONSTRAINT DF_department_created DEFAULT (SYSUTCDATETIME()),
    CONSTRAINT PK_department PRIMARY KEY CLUSTERED (department_id),
    CONSTRAINT UQ_department_code UNIQUE (code)
);

CREATE TABLE dbo.role (
    role_id           BIGINT IDENTITY(1,1) NOT NULL,
    code              NVARCHAR(64) NOT NULL,
    name              NVARCHAR(128) NOT NULL,
    is_system         BIT NOT NULL CONSTRAINT DF_role_system DEFAULT (1),
    is_active         BIT NOT NULL CONSTRAINT DF_role_active DEFAULT (1),
    created_at_utc    DATETIME2(3) NOT NULL CONSTRAINT DF_role_created DEFAULT (SYSUTCDATETIME()),
    CONSTRAINT PK_role PRIMARY KEY CLUSTERED (role_id),
    CONSTRAINT UQ_role_code UNIQUE (code)
);

CREATE TABLE dbo.permission (
    permission_id     BIGINT IDENTITY(1,1) NOT NULL,
    code              NVARCHAR(64) NOT NULL,
    description       NVARCHAR(256) NOT NULL,
    CONSTRAINT PK_permission PRIMARY KEY CLUSTERED (permission_id),
    CONSTRAINT UQ_permission_code UNIQUE (code)
);

CREATE TABLE dbo.role_permission (
    role_id           BIGINT NOT NULL,
    permission_id     BIGINT NOT NULL,
    CONSTRAINT PK_role_permission PRIMARY KEY CLUSTERED (role_id, permission_id),
    CONSTRAINT FK_role_permission_role FOREIGN KEY (role_id) REFERENCES dbo.role (role_id),
    CONSTRAINT FK_role_permission_perm FOREIGN KEY (permission_id) REFERENCES dbo.permission (permission_id)
);

CREATE TABLE dbo.employee (
    employee_id           BIGINT IDENTITY(1,1) NOT NULL,
    employee_no           NVARCHAR(20) NOT NULL,
    upn                   NVARCHAR(256) NULL,
    sam_account_name      NVARCHAR(64) NULL,
    display_name          NVARCHAR(128) NOT NULL,
    designation           NVARCHAR(128) NULL,
    email                 NVARCHAR(256) NULL,
    department_id         BIGINT NULL,
    manager_id            BIGINT NULL,
    hod_id                BIGINT NULL,
    portal_active         BIT NOT NULL CONSTRAINT DF_employee_portal_active DEFAULT (0),
    last_ldap_sync_utc    DATETIME2(3) NULL,
    notes                 NVARCHAR(512) NULL,
    created_at_utc        DATETIME2(3) NOT NULL CONSTRAINT DF_employee_created DEFAULT (SYSUTCDATETIME()),
    updated_at_utc        DATETIME2(3) NOT NULL CONSTRAINT DF_employee_updated DEFAULT (SYSUTCDATETIME()),
    CONSTRAINT PK_employee PRIMARY KEY CLUSTERED (employee_id),
    CONSTRAINT UQ_employee_no UNIQUE (employee_no),
    CONSTRAINT FK_employee_department FOREIGN KEY (department_id) REFERENCES dbo.department (department_id)
);

CREATE TABLE dbo.employee_role (
    employee_id       BIGINT NOT NULL,
    role_id           BIGINT NOT NULL,
    assigned_at_utc   DATETIME2(3) NOT NULL CONSTRAINT DF_employee_role_assigned DEFAULT (SYSUTCDATETIME()),
    assigned_by_id    BIGINT NULL,
    CONSTRAINT PK_employee_role PRIMARY KEY CLUSTERED (employee_id, role_id),
    CONSTRAINT FK_employee_role_employee FOREIGN KEY (employee_id) REFERENCES dbo.employee (employee_id),
    CONSTRAINT FK_employee_role_role FOREIGN KEY (role_id) REFERENCES dbo.role (role_id),
    CONSTRAINT FK_employee_role_assigned_by FOREIGN KEY (assigned_by_id) REFERENCES dbo.employee (employee_id)
);

CREATE TABLE dbo.ldap_sync_log (
    ldap_sync_log_id  BIGINT IDENTITY(1,1) NOT NULL,
    started_at_utc    DATETIME2(3) NOT NULL,
    finished_at_utc   DATETIME2(3) NULL,
    result_code       NVARCHAR(32) NOT NULL,
    inserted_count    INT NOT NULL CONSTRAINT DF_ldap_sync_ins DEFAULT (0),
    updated_count     INT NOT NULL CONSTRAINT DF_ldap_sync_upd DEFAULT (0),
    message           NVARCHAR(1000) NULL,
    CONSTRAINT PK_ldap_sync_log PRIMARY KEY CLUSTERED (ldap_sync_log_id)
);

CREATE TABLE dbo.assignment_group (
    assignment_group_id BIGINT IDENTITY(1,1) NOT NULL,
    code                NVARCHAR(64) NOT NULL,
    name                NVARCHAR(128) NOT NULL,
    is_active           BIT NOT NULL CONSTRAINT DF_asg_active DEFAULT (1),
    CONSTRAINT PK_assignment_group PRIMARY KEY CLUSTERED (assignment_group_id),
    CONSTRAINT UQ_assignment_group_code UNIQUE (code)
);

CREATE TABLE dbo.assignment_group_member (
    assignment_group_id BIGINT NOT NULL,
    employee_id         BIGINT NOT NULL,
    CONSTRAINT PK_assignment_group_member PRIMARY KEY CLUSTERED (assignment_group_id, employee_id),
    CONSTRAINT FK_agm_group FOREIGN KEY (assignment_group_id) REFERENCES dbo.assignment_group (assignment_group_id),
    CONSTRAINT FK_agm_employee FOREIGN KEY (employee_id) REFERENCES dbo.employee (employee_id)
);

/* =====================================================================
   Lookups, SLA, numbering, attachments
   ===================================================================== */

CREATE TABLE dbo.ticket_type (
    ticket_type_id    BIGINT IDENTITY(1,1) NOT NULL,
    code              NVARCHAR(64) NOT NULL,
    name              NVARCHAR(128) NOT NULL,
    sort_order        INT NOT NULL CONSTRAINT DF_ticket_type_sort DEFAULT (0),
    is_active         BIT NOT NULL CONSTRAINT DF_ticket_type_active DEFAULT (1),
    CONSTRAINT PK_ticket_type PRIMARY KEY CLUSTERED (ticket_type_id),
    CONSTRAINT UQ_ticket_type_code UNIQUE (code)
);

CREATE TABLE dbo.category (
    category_id       BIGINT IDENTITY(1,1) NOT NULL,
    code              NVARCHAR(64) NOT NULL,
    name              NVARCHAR(128) NOT NULL,
    sort_order        INT NOT NULL CONSTRAINT DF_category_sort DEFAULT (0),
    is_active         BIT NOT NULL CONSTRAINT DF_category_active DEFAULT (1),
    CONSTRAINT PK_category PRIMARY KEY CLUSTERED (category_id),
    CONSTRAINT UQ_category_code UNIQUE (code)
);

CREATE TABLE dbo.sub_category (
    sub_category_id   BIGINT IDENTITY(1,1) NOT NULL,
    category_id       BIGINT NOT NULL,
    code              NVARCHAR(64) NOT NULL,
    name              NVARCHAR(128) NOT NULL,
    sort_order        INT NOT NULL CONSTRAINT DF_subcat_sort DEFAULT (0),
    is_active         BIT NOT NULL CONSTRAINT DF_subcat_active DEFAULT (1),
    CONSTRAINT PK_sub_category PRIMARY KEY CLUSTERED (sub_category_id),
    CONSTRAINT UQ_sub_category_cat_code UNIQUE (category_id, code),
    CONSTRAINT FK_sub_category_category FOREIGN KEY (category_id) REFERENCES dbo.category (category_id)
);

CREATE TABLE dbo.sla_policy (
    sla_policy_id         BIGINT IDENTITY(1,1) NOT NULL,
    priority_code         NVARCHAR(16) NOT NULL,
    response_minutes      INT NOT NULL,
    resolution_minutes    INT NOT NULL,
    is_24x7               BIT NOT NULL CONSTRAINT DF_sla_24x7 DEFAULT (0),
    is_active             BIT NOT NULL CONSTRAINT DF_sla_active DEFAULT (1),
    CONSTRAINT PK_sla_policy PRIMARY KEY CLUSTERED (sla_policy_id),
    CONSTRAINT UQ_sla_policy_priority UNIQUE (priority_code),
    CONSTRAINT CK_sla_policy_priority CHECK (priority_code IN (N'Critical', N'High', N'Medium', N'Low')),
    CONSTRAINT CK_sla_policy_minutes CHECK (response_minutes > 0 AND resolution_minutes > 0)
);

CREATE TABLE dbo.ticket_number_config (
    ticket_number_config_id BIGINT IDENTITY(1,1) NOT NULL,
    prefix                  NVARCHAR(16) NOT NULL,
    include_year            BIT NOT NULL CONSTRAINT DF_tnc_year DEFAULT (1),
    padding                 INT NOT NULL CONSTRAINT DF_tnc_pad DEFAULT (6),
    sequence_year           INT NOT NULL,
    last_allocated          BIGINT NOT NULL CONSTRAINT DF_tnc_last DEFAULT (0),
    CONSTRAINT PK_ticket_number_config PRIMARY KEY CLUSTERED (ticket_number_config_id),
    CONSTRAINT UQ_ticket_number_year UNIQUE (sequence_year),
    CONSTRAINT CK_ticket_number_padding CHECK (padding >= 4 AND padding <= 10)
);

CREATE TABLE dbo.attachment_policy (
    attachment_policy_id  BIGINT IDENTITY(1,1) NOT NULL,
    max_bytes             INT NOT NULL,
    allowed_extensions    NVARCHAR(512) NOT NULL,
    allowed_mime_types    NVARCHAR(1000) NOT NULL,
    virus_scan_required   BIT NOT NULL CONSTRAINT DF_att_scan DEFAULT (0),
    CONSTRAINT PK_attachment_policy PRIMARY KEY CLUSTERED (attachment_policy_id),
    CONSTRAINT CK_attachment_policy_size CHECK (max_bytes > 0)
);

CREATE TABLE dbo.business_calendar (
    business_calendar_id  BIGINT IDENTITY(1,1) NOT NULL,
    weekday_iso           TINYINT NOT NULL,
    start_time            TIME(0) NOT NULL,
    end_time              TIME(0) NOT NULL,
    timezone_id           NVARCHAR(64) NOT NULL CONSTRAINT DF_cal_tz DEFAULT (N'India Standard Time'),
    is_working_day        BIT NOT NULL CONSTRAINT DF_cal_work DEFAULT (1),
    CONSTRAINT PK_business_calendar PRIMARY KEY CLUSTERED (business_calendar_id),
    CONSTRAINT UQ_business_calendar_wd UNIQUE (weekday_iso),
    CONSTRAINT CK_business_calendar_wd CHECK (weekday_iso BETWEEN 1 AND 7)
);

CREATE TABLE dbo.holiday (
    holiday_id        BIGINT IDENTITY(1,1) NOT NULL,
    holiday_date      DATE NOT NULL,
    name              NVARCHAR(128) NOT NULL,
    is_national       BIT NOT NULL CONSTRAINT DF_holiday_nat DEFAULT (1),
    CONSTRAINT PK_holiday PRIMARY KEY CLUSTERED (holiday_id),
    CONSTRAINT UQ_holiday_date UNIQUE (holiday_date)
);

/* =====================================================================
   Dynamic workflow masters
   ===================================================================== */

CREATE TABLE dbo.workflow_definition (
    workflow_definition_id BIGINT IDENTITY(1,1) NOT NULL,
    code                   NVARCHAR(64) NOT NULL,
    name                   NVARCHAR(128) NOT NULL,
    version_no             INT NOT NULL CONSTRAINT DF_wfdef_ver DEFAULT (1),
    status_code            NVARCHAR(32) NOT NULL,
    description            NVARCHAR(512) NULL,
    created_at_utc         DATETIME2(3) NOT NULL CONSTRAINT DF_wfdef_created DEFAULT (SYSUTCDATETIME()),
    CONSTRAINT PK_workflow_definition PRIMARY KEY CLUSTERED (workflow_definition_id),
    CONSTRAINT UQ_workflow_definition_code_ver UNIQUE (code, version_no),
    CONSTRAINT CK_workflow_definition_status CHECK (status_code IN (N'Draft', N'PendingChecker', N'Active', N'Retired', N'Inactive'))
);

CREATE TABLE dbo.workflow_stage (
    workflow_stage_id       BIGINT IDENTITY(1,1) NOT NULL,
    workflow_definition_id  BIGINT NOT NULL,
    stage_order             INT NOT NULL,
    code                    NVARCHAR(64) NOT NULL,
    label                   NVARCHAR(128) NOT NULL,
    stage_type              NVARCHAR(32) NOT NULL,
    actor_strategy          NVARCHAR(64) NOT NULL,
    role_id                 BIGINT NULL,
    assignment_group_id     BIGINT NULL,
    send_back_target        NVARCHAR(32) NULL,
    CONSTRAINT PK_workflow_stage PRIMARY KEY CLUSTERED (workflow_stage_id),
    CONSTRAINT UQ_workflow_stage_def_order UNIQUE (workflow_definition_id, stage_order),
    CONSTRAINT UQ_workflow_stage_def_code UNIQUE (workflow_definition_id, code),
    CONSTRAINT FK_workflow_stage_def FOREIGN KEY (workflow_definition_id) REFERENCES dbo.workflow_definition (workflow_definition_id),
    CONSTRAINT FK_workflow_stage_role FOREIGN KEY (role_id) REFERENCES dbo.role (role_id),
    CONSTRAINT FK_workflow_stage_asg FOREIGN KEY (assignment_group_id) REFERENCES dbo.assignment_group (assignment_group_id),
    CONSTRAINT CK_workflow_stage_type CHECK (stage_type IN (N'APPROVAL', N'ASSIGNMENT', N'FULFILMENT', N'CONFIRMATION', N'CLOSURE')),
    CONSTRAINT CK_workflow_stage_strategy CHECK (actor_strategy IN (
        N'DYNAMIC_HIERARCHY_TO_HOD', N'LDAP_MANAGER', N'LDAP_HOD', N'NAMED_ROLE',
        N'ASSIGNMENT_GROUP', N'SERVICE_DESK', N'IMPLEMENTOR', N'REQUESTER', N'SYSTEM',
        N'ASSET_OWNER', N'NAMED_EMPLOYEE')),
    CONSTRAINT CK_workflow_stage_sendback CHECK (send_back_target IS NULL OR send_back_target IN (N'PREVIOUS_STAGE', N'REQUESTER'))
);

CREATE TABLE dbo.workflow_stage_transition (
    workflow_stage_transition_id BIGINT IDENTITY(1,1) NOT NULL,
    workflow_stage_id            BIGINT NOT NULL,
    action_code                  NVARCHAR(32) NOT NULL,
    remarks_required             BIT NOT NULL CONSTRAINT DF_wfst_remarks DEFAULT (0),
    CONSTRAINT PK_workflow_stage_transition PRIMARY KEY CLUSTERED (workflow_stage_transition_id),
    CONSTRAINT UQ_workflow_stage_transition UNIQUE (workflow_stage_id, action_code),
    CONSTRAINT FK_wfst_stage FOREIGN KEY (workflow_stage_id) REFERENCES dbo.workflow_stage (workflow_stage_id),
    CONSTRAINT CK_wfst_action CHECK (action_code IN (
        N'APPROVE', N'REJECT', N'SEND_BACK', N'COMPLETE',
        N'ACCEPT', N'START', N'HOLD', N'RESOLVE', N'REASSIGN', N'ASSIGN'))
);

CREATE TABLE dbo.workflow_rule (
    workflow_rule_id         BIGINT IDENTITY(1,1) NOT NULL,
    name                     NVARCHAR(128) NOT NULL,
    priority                 INT NOT NULL,
    status_code              NVARCHAR(32) NOT NULL,
    condition_json           NVARCHAR(MAX) NOT NULL,
    workflow_definition_id   BIGINT NOT NULL,
    CONSTRAINT PK_workflow_rule PRIMARY KEY CLUSTERED (workflow_rule_id),
    CONSTRAINT FK_workflow_rule_def FOREIGN KEY (workflow_definition_id) REFERENCES dbo.workflow_definition (workflow_definition_id),
    CONSTRAINT CK_workflow_rule_status CHECK (status_code IN (N'Active', N'Inactive', N'PendingChecker'))
);

/* =====================================================================
   Assets, tickets, instances
   ===================================================================== */

CREATE TABLE dbo.asset (
    asset_id          BIGINT IDENTITY(1,1) NOT NULL,
    asset_tag         NVARCHAR(32) NOT NULL,
    asset_type        NVARCHAR(64) NOT NULL,
    serial_number     NVARCHAR(64) NULL,
    employee_id       BIGINT NULL,
    department_id     BIGINT NULL,
    location          NVARCHAR(128) NULL,
    purchase_date     DATE NULL,
    warranty_end      DATE NULL,
    status_code       NVARCHAR(32) NOT NULL,
    operating_system  NVARCHAR(64) NULL,
    ip_address        NVARCHAR(45) NULL,
    updated_at_utc    DATETIME2(3) NOT NULL CONSTRAINT DF_asset_updated DEFAULT (SYSUTCDATETIME()),
    CONSTRAINT PK_asset PRIMARY KEY CLUSTERED (asset_id),
    CONSTRAINT UQ_asset_tag UNIQUE (asset_tag),
    CONSTRAINT FK_asset_employee FOREIGN KEY (employee_id) REFERENCES dbo.employee (employee_id),
    CONSTRAINT FK_asset_department FOREIGN KEY (department_id) REFERENCES dbo.department (department_id)
);

CREATE TABLE dbo.workflow_instance (
    workflow_instance_id     BIGINT IDENTITY(1,1) NOT NULL,
    ticket_id                BIGINT NOT NULL,
    workflow_definition_id   BIGINT NOT NULL,
    workflow_rule_id         BIGINT NULL,
    current_stage_id         BIGINT NULL,
    status_code              NVARCHAR(32) NOT NULL,
    created_at_utc           DATETIME2(3) NOT NULL CONSTRAINT DF_wfi_created DEFAULT (SYSUTCDATETIME()),
    CONSTRAINT PK_workflow_instance PRIMARY KEY CLUSTERED (workflow_instance_id),
    CONSTRAINT UQ_workflow_instance_ticket UNIQUE (ticket_id),
    CONSTRAINT FK_wfi_definition FOREIGN KEY (workflow_definition_id) REFERENCES dbo.workflow_definition (workflow_definition_id),
    CONSTRAINT FK_wfi_rule FOREIGN KEY (workflow_rule_id) REFERENCES dbo.workflow_rule (workflow_rule_id)
);

CREATE TABLE dbo.ticket (
    ticket_id                 BIGINT IDENTITY(1,1) NOT NULL,
    public_number             NVARCHAR(32) NOT NULL,
    ticket_type_id            BIGINT NOT NULL,
    category_id               BIGINT NOT NULL,
    sub_category_id           BIGINT NOT NULL,
    subject                   NVARCHAR(256) NOT NULL,
    description               NVARCHAR(MAX) NOT NULL,
    priority_code             NVARCHAR(16) NOT NULL,
    impact_code               NVARCHAR(32) NOT NULL,
    urgency_code              NVARCHAR(16) NOT NULL,
    confidentiality_code      NVARCHAR(32) NOT NULL,
    location                  NVARCHAR(128) NULL,
    asset_id                  BIGINT NULL,
    application_name          NVARCHAR(128) NULL,
    required_date             DATE NULL,
    requester_id              BIGINT NOT NULL,
    department_id             BIGINT NULL,
    status_code               NVARCHAR(32) NOT NULL,
    progress_code             NVARCHAR(64) NULL,
    assigned_group_id         BIGINT NULL,
    assigned_implementor_id   BIGINT NULL,
    workflow_instance_id      BIGINT NULL,
    major_incident            BIT NOT NULL CONSTRAINT DF_ticket_major DEFAULT (0),
    reject_reason             NVARCHAR(1000) NULL,
    created_at_utc            DATETIME2(3) NOT NULL CONSTRAINT DF_ticket_created DEFAULT (SYSUTCDATETIME()),
    updated_at_utc            DATETIME2(3) NOT NULL CONSTRAINT DF_ticket_updated DEFAULT (SYSUTCDATETIME()),
    CONSTRAINT PK_ticket PRIMARY KEY CLUSTERED (ticket_id),
    CONSTRAINT UQ_ticket_public_number UNIQUE (public_number),
    CONSTRAINT FK_ticket_type FOREIGN KEY (ticket_type_id) REFERENCES dbo.ticket_type (ticket_type_id),
    CONSTRAINT FK_ticket_category FOREIGN KEY (category_id) REFERENCES dbo.category (category_id),
    CONSTRAINT FK_ticket_subcategory FOREIGN KEY (sub_category_id) REFERENCES dbo.sub_category (sub_category_id),
    CONSTRAINT FK_ticket_requester FOREIGN KEY (requester_id) REFERENCES dbo.employee (employee_id),
    CONSTRAINT FK_ticket_department FOREIGN KEY (department_id) REFERENCES dbo.department (department_id),
    CONSTRAINT FK_ticket_asset FOREIGN KEY (asset_id) REFERENCES dbo.asset (asset_id),
    CONSTRAINT FK_ticket_asg FOREIGN KEY (assigned_group_id) REFERENCES dbo.assignment_group (assignment_group_id),
    CONSTRAINT FK_ticket_implementor FOREIGN KEY (assigned_implementor_id) REFERENCES dbo.employee (employee_id),
    CONSTRAINT CK_ticket_priority CHECK (priority_code IN (N'Critical', N'High', N'Medium', N'Low')),
    CONSTRAINT CK_ticket_impact CHECK (impact_code IN (N'Individual', N'Department', N'Multiple Departments', N'Organization-wide')),
    CONSTRAINT CK_ticket_urgency CHECK (urgency_code IN (N'Critical', N'High', N'Medium', N'Low')),
    CONSTRAINT CK_ticket_confidentiality CHECK (confidentiality_code IN (N'Normal', N'Confidential', N'Highly Confidential')),
    CONSTRAINT CK_ticket_status CHECK (status_code IN (
        N'Draft', N'Pending Approval', N'Approved', N'Assigned', N'In Progress',
        N'On Hold', N'Resolved', N'Closed', N'Rejected'))
);

CREATE TABLE dbo.ticket_sla (
    ticket_sla_id         BIGINT IDENTITY(1,1) NOT NULL,
    ticket_id             BIGINT NOT NULL,
    sla_policy_id         BIGINT NOT NULL,
    sla_start_utc         DATETIME2(3) NOT NULL,
    response_due_utc      DATETIME2(3) NOT NULL,
    resolve_due_utc       DATETIME2(3) NOT NULL,
    first_response_utc    DATETIME2(3) NULL,
    resolved_utc          DATETIME2(3) NULL,
    paused                BIT NOT NULL CONSTRAINT DF_tsla_paused DEFAULT (0),
    state_code            NVARCHAR(16) NOT NULL,
    CONSTRAINT PK_ticket_sla PRIMARY KEY CLUSTERED (ticket_sla_id),
    CONSTRAINT UQ_ticket_sla_ticket UNIQUE (ticket_id),
    CONSTRAINT FK_ticket_sla_ticket FOREIGN KEY (ticket_id) REFERENCES dbo.ticket (ticket_id),
    CONSTRAINT FK_ticket_sla_policy FOREIGN KEY (sla_policy_id) REFERENCES dbo.sla_policy (sla_policy_id),
    CONSTRAINT CK_ticket_sla_state CHECK (state_code IN (N'WITHIN', N'NEAR', N'BREACHED', N'MET'))
);

CREATE TABLE dbo.workflow_instance_stage (
    workflow_instance_stage_id BIGINT IDENTITY(1,1) NOT NULL,
    workflow_instance_id       BIGINT NOT NULL,
    workflow_stage_id          BIGINT NULL,
    stage_order                INT NOT NULL,
    code                       NVARCHAR(64) NOT NULL,
    label                      NVARCHAR(128) NOT NULL,
    stage_type                 NVARCHAR(32) NOT NULL,
    actor_strategy             NVARCHAR(64) NOT NULL,
    resolved_employee_id       BIGINT NULL,
    resolved_role_id           BIGINT NULL,
    resolved_group_id          BIGINT NULL,
    status_code                NVARCHAR(32) NOT NULL,
    action_code                NVARCHAR(32) NULL,
    remarks                    NVARCHAR(2000) NULL,
    acted_at_utc               DATETIME2(3) NULL,
    CONSTRAINT PK_workflow_instance_stage PRIMARY KEY CLUSTERED (workflow_instance_stage_id),
    CONSTRAINT UQ_wfis_instance_order UNIQUE (workflow_instance_id, stage_order),
    CONSTRAINT FK_wfis_instance FOREIGN KEY (workflow_instance_id) REFERENCES dbo.workflow_instance (workflow_instance_id),
    CONSTRAINT FK_wfis_stage FOREIGN KEY (workflow_stage_id) REFERENCES dbo.workflow_stage (workflow_stage_id),
    CONSTRAINT FK_wfis_employee FOREIGN KEY (resolved_employee_id) REFERENCES dbo.employee (employee_id),
    CONSTRAINT FK_wfis_role FOREIGN KEY (resolved_role_id) REFERENCES dbo.role (role_id),
    CONSTRAINT FK_wfis_group FOREIGN KEY (resolved_group_id) REFERENCES dbo.assignment_group (assignment_group_id),
    CONSTRAINT CK_wfis_status CHECK (status_code IN (N'Pending', N'Current', N'Completed', N'Rejected', N'Skipped'))
);

CREATE TABLE dbo.ticket_comment (
    ticket_comment_id     BIGINT IDENTITY(1,1) NOT NULL,
    ticket_id             BIGINT NOT NULL,
    author_id             BIGINT NOT NULL,
    body                  NVARCHAR(MAX) NOT NULL,
    is_internal           BIT NOT NULL CONSTRAINT DF_comment_internal DEFAULT (0),
    created_at_utc        DATETIME2(3) NOT NULL CONSTRAINT DF_comment_created DEFAULT (SYSUTCDATETIME()),
    CONSTRAINT PK_ticket_comment PRIMARY KEY CLUSTERED (ticket_comment_id),
    CONSTRAINT FK_comment_ticket FOREIGN KEY (ticket_id) REFERENCES dbo.ticket (ticket_id),
    CONSTRAINT FK_comment_author FOREIGN KEY (author_id) REFERENCES dbo.employee (employee_id)
);

CREATE TABLE dbo.ticket_attachment (
    ticket_attachment_id  BIGINT IDENTITY(1,1) NOT NULL,
    ticket_id             BIGINT NOT NULL,
    original_name         NVARCHAR(256) NOT NULL,
    storage_key           NVARCHAR(512) NOT NULL,
    content_type          NVARCHAR(128) NULL,
    byte_length           INT NOT NULL,
    uploaded_by_id        BIGINT NOT NULL,
    uploaded_at_utc       DATETIME2(3) NOT NULL CONSTRAINT DF_tatt_uploaded DEFAULT (SYSUTCDATETIME()),
    CONSTRAINT PK_ticket_attachment PRIMARY KEY CLUSTERED (ticket_attachment_id),
    CONSTRAINT FK_tatt_ticket FOREIGN KEY (ticket_id) REFERENCES dbo.ticket (ticket_id),
    CONSTRAINT FK_tatt_uploader FOREIGN KEY (uploaded_by_id) REFERENCES dbo.employee (employee_id)
);

CREATE TABLE dbo.comment_attachment (
    comment_attachment_id BIGINT IDENTITY(1,1) NOT NULL,
    ticket_comment_id     BIGINT NOT NULL,
    original_name         NVARCHAR(256) NOT NULL,
    storage_key           NVARCHAR(512) NOT NULL,
    byte_length           INT NOT NULL,
    uploaded_by_id        BIGINT NOT NULL,
    uploaded_at_utc       DATETIME2(3) NOT NULL CONSTRAINT DF_catt_uploaded DEFAULT (SYSUTCDATETIME()),
    CONSTRAINT PK_comment_attachment PRIMARY KEY CLUSTERED (comment_attachment_id),
    CONSTRAINT FK_catt_comment FOREIGN KEY (ticket_comment_id) REFERENCES dbo.ticket_comment (ticket_comment_id),
    CONSTRAINT FK_catt_uploader FOREIGN KEY (uploaded_by_id) REFERENCES dbo.employee (employee_id)
);

CREATE TABLE dbo.ticket_relation (
    ticket_id         BIGINT NOT NULL,
    related_ticket_id BIGINT NOT NULL,
    relation_code     NVARCHAR(32) NOT NULL CONSTRAINT DF_trel_code DEFAULT (N'RELATED'),
    CONSTRAINT PK_ticket_relation PRIMARY KEY CLUSTERED (ticket_id, related_ticket_id),
    CONSTRAINT FK_trel_ticket FOREIGN KEY (ticket_id) REFERENCES dbo.ticket (ticket_id),
    CONSTRAINT FK_trel_related FOREIGN KEY (related_ticket_id) REFERENCES dbo.ticket (ticket_id),
    CONSTRAINT CK_trel_not_self CHECK (ticket_id <> related_ticket_id)
);

CREATE TABLE dbo.escalation_matrix (
    escalation_matrix_id  BIGINT IDENTITY(1,1) NOT NULL,
    trigger_code          NVARCHAR(16) NOT NULL,
    priority_code         NVARCHAR(16) NOT NULL,
    notify_role_id        BIGINT NULL,
    notify_group_id       BIGINT NULL,
    CONSTRAINT PK_escalation_matrix PRIMARY KEY CLUSTERED (escalation_matrix_id),
    CONSTRAINT FK_esc_role FOREIGN KEY (notify_role_id) REFERENCES dbo.role (role_id),
    CONSTRAINT FK_esc_group FOREIGN KEY (notify_group_id) REFERENCES dbo.assignment_group (assignment_group_id),
    CONSTRAINT CK_esc_trigger CHECK (trigger_code IN (N'NEAR', N'BREACHED')),
    CONSTRAINT CK_esc_priority CHECK (priority_code IN (N'Critical', N'High', N'Medium', N'Low'))
);

CREATE TABLE dbo.escalation_event (
    escalation_event_id   BIGINT IDENTITY(1,1) NOT NULL,
    ticket_id             BIGINT NOT NULL,
    escalation_matrix_id  BIGINT NOT NULL,
    fired_at_utc          DATETIME2(3) NOT NULL CONSTRAINT DF_escev_fired DEFAULT (SYSUTCDATETIME()),
    CONSTRAINT PK_escalation_event PRIMARY KEY CLUSTERED (escalation_event_id),
    CONSTRAINT FK_escev_ticket FOREIGN KEY (ticket_id) REFERENCES dbo.ticket (ticket_id),
    CONSTRAINT FK_escev_matrix FOREIGN KEY (escalation_matrix_id) REFERENCES dbo.escalation_matrix (escalation_matrix_id)
);

/* =====================================================================
   KB, notifications, settings, maker-checker, audit
   ===================================================================== */

CREATE TABLE dbo.kb_category (
    kb_category_id    BIGINT IDENTITY(1,1) NOT NULL,
    code              NVARCHAR(64) NOT NULL,
    name              NVARCHAR(128) NOT NULL,
    CONSTRAINT PK_kb_category PRIMARY KEY CLUSTERED (kb_category_id),
    CONSTRAINT UQ_kb_category_code UNIQUE (code)
);

CREATE TABLE dbo.kb_article (
    kb_article_id     BIGINT IDENTITY(1,1) NOT NULL,
    kb_category_id    BIGINT NOT NULL,
    title             NVARCHAR(256) NOT NULL,
    body              NVARCHAR(MAX) NOT NULL,
    is_published      BIT NOT NULL CONSTRAINT DF_kb_pub DEFAULT (1),
    created_at_utc    DATETIME2(3) NOT NULL CONSTRAINT DF_kb_created DEFAULT (SYSUTCDATETIME()),
    CONSTRAINT PK_kb_article PRIMARY KEY CLUSTERED (kb_article_id),
    CONSTRAINT FK_kb_article_cat FOREIGN KEY (kb_category_id) REFERENCES dbo.kb_category (kb_category_id)
);

CREATE TABLE dbo.kb_article_feedback (
    kb_article_id     BIGINT NOT NULL,
    employee_id       BIGINT NOT NULL,
    is_helpful        BIT NOT NULL,
    CONSTRAINT PK_kb_article_feedback PRIMARY KEY CLUSTERED (kb_article_id, employee_id),
    CONSTRAINT FK_kbf_article FOREIGN KEY (kb_article_id) REFERENCES dbo.kb_article (kb_article_id),
    CONSTRAINT FK_kbf_employee FOREIGN KEY (employee_id) REFERENCES dbo.employee (employee_id)
);

CREATE TABLE dbo.kb_article_ticket (
    kb_article_id     BIGINT NOT NULL,
    ticket_id         BIGINT NOT NULL,
    CONSTRAINT PK_kb_article_ticket PRIMARY KEY CLUSTERED (kb_article_id, ticket_id),
    CONSTRAINT FK_kbt_article FOREIGN KEY (kb_article_id) REFERENCES dbo.kb_article (kb_article_id),
    CONSTRAINT FK_kbt_ticket FOREIGN KEY (ticket_id) REFERENCES dbo.ticket (ticket_id)
);

CREATE TABLE dbo.notification_rule (
    notification_rule_id  BIGINT IDENTITY(1,1) NOT NULL,
    event_code            NVARCHAR(64) NOT NULL,
    in_app                BIT NOT NULL CONSTRAINT DF_nr_app DEFAULT (1),
    send_email            BIT NOT NULL CONSTRAINT DF_nr_mail DEFAULT (1),
    is_active             BIT NOT NULL CONSTRAINT DF_nr_active DEFAULT (1),
    CONSTRAINT PK_notification_rule PRIMARY KEY CLUSTERED (notification_rule_id),
    CONSTRAINT UQ_notification_rule_event UNIQUE (event_code)
);

CREATE TABLE dbo.email_template (
    email_template_id     BIGINT IDENTITY(1,1) NOT NULL,
    code                  NVARCHAR(64) NOT NULL,
    subject               NVARCHAR(256) NOT NULL,
    body                  NVARCHAR(MAX) NOT NULL,
    CONSTRAINT PK_email_template PRIMARY KEY CLUSTERED (email_template_id),
    CONSTRAINT UQ_email_template_code UNIQUE (code)
);

CREATE TABLE dbo.notification (
    notification_id   BIGINT IDENTITY(1,1) NOT NULL,
    recipient_id      BIGINT NOT NULL,
    ticket_id         BIGINT NULL,
    title             NVARCHAR(256) NOT NULL,
    body              NVARCHAR(1000) NOT NULL,
    is_read           BIT NOT NULL CONSTRAINT DF_notif_read DEFAULT (0),
    created_at_utc    DATETIME2(3) NOT NULL CONSTRAINT DF_notif_created DEFAULT (SYSUTCDATETIME()),
    CONSTRAINT PK_notification PRIMARY KEY CLUSTERED (notification_id),
    CONSTRAINT FK_notif_recipient FOREIGN KEY (recipient_id) REFERENCES dbo.employee (employee_id),
    CONSTRAINT FK_notif_ticket FOREIGN KEY (ticket_id) REFERENCES dbo.ticket (ticket_id)
);

CREATE TABLE dbo.system_setting (
    setting_key       NVARCHAR(128) NOT NULL,
    setting_value     NVARCHAR(512) NOT NULL,
    category          NVARCHAR(64) NOT NULL,
    description       NVARCHAR(256) NULL,
    is_secret         BIT NOT NULL CONSTRAINT DF_setting_secret DEFAULT (0),
    CONSTRAINT PK_system_setting PRIMARY KEY CLUSTERED (setting_key),
    CONSTRAINT CK_system_setting_nosecret CHECK (is_secret = 0)
);

CREATE TABLE dbo.config_change_request (
    config_change_request_id BIGINT IDENTITY(1,1) NOT NULL,
    change_type              NVARCHAR(64) NOT NULL,
    entity_name              NVARCHAR(128) NOT NULL,
    entity_key               NVARCHAR(128) NULL,
    payload_json             NVARCHAR(MAX) NOT NULL,
    previous_json            NVARCHAR(MAX) NULL,
    description              NVARCHAR(512) NOT NULL,
    status_code              NVARCHAR(32) NOT NULL,
    requested_by_id          BIGINT NOT NULL,
    requested_at_utc         DATETIME2(3) NOT NULL CONSTRAINT DF_ccr_req DEFAULT (SYSUTCDATETIME()),
    reviewed_by_id           BIGINT NULL,
    reviewed_at_utc          DATETIME2(3) NULL,
    reject_reason            NVARCHAR(1000) NULL,
    CONSTRAINT PK_config_change_request PRIMARY KEY CLUSTERED (config_change_request_id),
    CONSTRAINT FK_ccr_requester FOREIGN KEY (requested_by_id) REFERENCES dbo.employee (employee_id),
    CONSTRAINT FK_ccr_reviewer FOREIGN KEY (reviewed_by_id) REFERENCES dbo.employee (employee_id),
    CONSTRAINT CK_ccr_status CHECK (status_code IN (N'PendingApproval', N'Applied', N'Rejected'))
);

CREATE TABLE dbo.audit_log (
    audit_log_id      BIGINT IDENTITY(1,1) NOT NULL,
    occurred_at_utc   DATETIME2(3) NOT NULL CONSTRAINT DF_audit_at DEFAULT (SYSUTCDATETIME()),
    employee_id       BIGINT NULL,
    employee_no       NVARCHAR(20) NULL,
    role_code         NVARCHAR(64) NULL,
    module_code       NVARCHAR(64) NOT NULL,
    action_code       NVARCHAR(64) NOT NULL,
    ticket_id         BIGINT NULL,
    old_value         NVARCHAR(MAX) NULL,
    new_value         NVARCHAR(MAX) NULL,
    ip_address        NVARCHAR(45) NULL,
    user_agent        NVARCHAR(256) NULL,
    result_code       NVARCHAR(16) NOT NULL CONSTRAINT DF_audit_result DEFAULT (N'SUCCESS'),
    CONSTRAINT PK_audit_log PRIMARY KEY CLUSTERED (audit_log_id)
);
GO

/* Deferred FKs (self-refs and ticket <-> instance) */
ALTER TABLE dbo.employee ADD CONSTRAINT FK_employee_manager
    FOREIGN KEY (manager_id) REFERENCES dbo.employee (employee_id);
ALTER TABLE dbo.employee ADD CONSTRAINT FK_employee_hod
    FOREIGN KEY (hod_id) REFERENCES dbo.employee (employee_id);

ALTER TABLE dbo.workflow_instance ADD CONSTRAINT FK_wfi_ticket
    FOREIGN KEY (ticket_id) REFERENCES dbo.ticket (ticket_id);
ALTER TABLE dbo.ticket ADD CONSTRAINT FK_ticket_wfi
    FOREIGN KEY (workflow_instance_id) REFERENCES dbo.workflow_instance (workflow_instance_id);

ALTER TABLE dbo.workflow_instance ADD CONSTRAINT FK_wfi_current_stage
    FOREIGN KEY (current_stage_id) REFERENCES dbo.workflow_instance_stage (workflow_instance_stage_id);
GO

CREATE TRIGGER dbo.trg_audit_log_immutable
ON dbo.audit_log
AFTER UPDATE, DELETE
AS
BEGIN
    SET NOCOUNT ON;
    ROLLBACK TRANSACTION;
    RAISERROR(N'audit_log is immutable: UPDATE and DELETE are not permitted.', 16, 1);
END
GO


/* JSON checks need ISJSON (SQL Server 2016+). Added through dynamic SQL so this script also runs on
   SQL Server 2012/2014, where the application alone validates the JSON before saving. */
IF CAST(PARSENAME(CAST(SERVERPROPERTY('ProductVersion') AS NVARCHAR(128)), 4) AS INT) >= 13
BEGIN
    EXEC sys.sp_executesql N'ALTER TABLE dbo.workflow_rule ADD CONSTRAINT CK_workflow_rule_json CHECK (ISJSON(condition_json) = 1)';
    EXEC sys.sp_executesql N'ALTER TABLE dbo.config_change_request ADD CONSTRAINT CK_ccr_payload CHECK (ISJSON(payload_json) = 1)';
END
GO

GO

/* ===================================================================== V2__mandatory_remarks_trigger.sql */
PRINT N'V2__mandatory_remarks_trigger';
GO
/*
  Flyway V2 — aligned with database/constraints.sql
*/
/*
  Extra constraints / triggers not inlined in tables.sql
  SQL Server 2016 / 2019 (compat 130). Uses LTRIM/RTRIM (not TRIM).
*/
SET NOCOUNT ON;
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
GO

GO

IF OBJECT_ID(N'dbo.trg_wfis_mandatory_remarks', N'TR') IS NOT NULL
    DROP TRIGGER dbo.trg_wfis_mandatory_remarks;
GO

CREATE TRIGGER dbo.trg_wfis_mandatory_remarks
ON dbo.workflow_instance_stage
AFTER INSERT, UPDATE
AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS (
        SELECT 1
        FROM inserted i
        WHERE i.action_code IN (N'APPROVE', N'REJECT', N'SEND_BACK')
          AND i.status_code IN (N'Completed', N'Rejected')
          AND (i.remarks IS NULL OR LEN(LTRIM(RTRIM(i.remarks))) = 0)
    )
    BEGIN
        ROLLBACK TRANSACTION;
        RAISERROR(N'Remarks are mandatory on Approve, Reject, and Send Back.', 16, 1);
        RETURN;
    END
END
GO


GO

/* ===================================================================== V3__indexes.sql */
PRINT N'V3__indexes';
GO
/* Flyway V3 — aligned with database/indexes.sql */
/*
  Nonclustered indexes — SQL Server 2016 / 2019 (compat 130).
  Run after tables.sql.
*/
SET NOCOUNT ON;
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
GO

GO

IF EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'UQ_workflow_rule_active_priority' AND object_id = OBJECT_ID(N'dbo.workflow_rule'))
    DROP INDEX UQ_workflow_rule_active_priority ON dbo.workflow_rule;
CREATE UNIQUE NONCLUSTERED INDEX UQ_workflow_rule_active_priority
    ON dbo.workflow_rule (priority)
    WHERE status_code = N'Active';
GO

IF EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_employee_department' AND object_id = OBJECT_ID(N'dbo.employee'))
    DROP INDEX IX_employee_department ON dbo.employee;
CREATE NONCLUSTERED INDEX IX_employee_department ON dbo.employee (department_id) INCLUDE (employee_no, display_name, portal_active);

IF EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_employee_manager' AND object_id = OBJECT_ID(N'dbo.employee'))
    DROP INDEX IX_employee_manager ON dbo.employee;
CREATE NONCLUSTERED INDEX IX_employee_manager ON dbo.employee (manager_id);

IF EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_employee_hod' AND object_id = OBJECT_ID(N'dbo.employee'))
    DROP INDEX IX_employee_hod ON dbo.employee;
CREATE NONCLUSTERED INDEX IX_employee_hod ON dbo.employee (hod_id);

IF EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_employee_sam' AND object_id = OBJECT_ID(N'dbo.employee'))
    DROP INDEX IX_employee_sam ON dbo.employee;
CREATE NONCLUSTERED INDEX IX_employee_sam ON dbo.employee (sam_account_name) WHERE sam_account_name IS NOT NULL;

IF EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_ticket_requester' AND object_id = OBJECT_ID(N'dbo.ticket'))
    DROP INDEX IX_ticket_requester ON dbo.ticket;
CREATE NONCLUSTERED INDEX IX_ticket_requester ON dbo.ticket (requester_id, created_at_utc DESC);

IF EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_ticket_status' AND object_id = OBJECT_ID(N'dbo.ticket'))
    DROP INDEX IX_ticket_status ON dbo.ticket;
CREATE NONCLUSTERED INDEX IX_ticket_status ON dbo.ticket (status_code, priority_code) INCLUDE (public_number, subject);

IF EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_ticket_type_cat' AND object_id = OBJECT_ID(N'dbo.ticket'))
    DROP INDEX IX_ticket_type_cat ON dbo.ticket;
CREATE NONCLUSTERED INDEX IX_ticket_type_cat ON dbo.ticket (ticket_type_id, category_id, sub_category_id);

IF EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_ticket_implementor' AND object_id = OBJECT_ID(N'dbo.ticket'))
    DROP INDEX IX_ticket_implementor ON dbo.ticket;
CREATE NONCLUSTERED INDEX IX_ticket_implementor ON dbo.ticket (assigned_implementor_id) WHERE assigned_implementor_id IS NOT NULL;

IF EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_ticket_department' AND object_id = OBJECT_ID(N'dbo.ticket'))
    DROP INDEX IX_ticket_department ON dbo.ticket;
CREATE NONCLUSTERED INDEX IX_ticket_department ON dbo.ticket (department_id, status_code);

IF EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_wfis_instance_status' AND object_id = OBJECT_ID(N'dbo.workflow_instance_stage'))
    DROP INDEX IX_wfis_instance_status ON dbo.workflow_instance_stage;
CREATE NONCLUSTERED INDEX IX_wfis_instance_status ON dbo.workflow_instance_stage (workflow_instance_id, status_code) INCLUDE (resolved_employee_id, stage_order);

IF EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_wfis_actor' AND object_id = OBJECT_ID(N'dbo.workflow_instance_stage'))
    DROP INDEX IX_wfis_actor ON dbo.workflow_instance_stage;
CREATE NONCLUSTERED INDEX IX_wfis_actor ON dbo.workflow_instance_stage (resolved_employee_id, status_code) WHERE resolved_employee_id IS NOT NULL;

IF EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_audit_occurred' AND object_id = OBJECT_ID(N'dbo.audit_log'))
    DROP INDEX IX_audit_occurred ON dbo.audit_log;
CREATE NONCLUSTERED INDEX IX_audit_occurred ON dbo.audit_log (occurred_at_utc DESC, action_code);

IF EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_audit_ticket' AND object_id = OBJECT_ID(N'dbo.audit_log'))
    DROP INDEX IX_audit_ticket ON dbo.audit_log;
CREATE NONCLUSTERED INDEX IX_audit_ticket ON dbo.audit_log (ticket_id, occurred_at_utc DESC) WHERE ticket_id IS NOT NULL;

IF EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_audit_employee' AND object_id = OBJECT_ID(N'dbo.audit_log'))
    DROP INDEX IX_audit_employee ON dbo.audit_log;
CREATE NONCLUSTERED INDEX IX_audit_employee ON dbo.audit_log (employee_no, occurred_at_utc DESC);

IF EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_notification_recipient' AND object_id = OBJECT_ID(N'dbo.notification'))
    DROP INDEX IX_notification_recipient ON dbo.notification;
CREATE NONCLUSTERED INDEX IX_notification_recipient ON dbo.notification (recipient_id, is_read, created_at_utc DESC);

IF EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_ccr_status' AND object_id = OBJECT_ID(N'dbo.config_change_request'))
    DROP INDEX IX_ccr_status ON dbo.config_change_request;
CREATE NONCLUSTERED INDEX IX_ccr_status ON dbo.config_change_request (status_code, requested_at_utc DESC);

IF EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_ticket_comment_ticket' AND object_id = OBJECT_ID(N'dbo.ticket_comment'))
    DROP INDEX IX_ticket_comment_ticket ON dbo.ticket_comment;
CREATE NONCLUSTERED INDEX IX_ticket_comment_ticket ON dbo.ticket_comment (ticket_id, created_at_utc);

IF EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_asset_employee' AND object_id = OBJECT_ID(N'dbo.asset'))
    DROP INDEX IX_asset_employee ON dbo.asset;
CREATE NONCLUSTERED INDEX IX_asset_employee ON dbo.asset (employee_id) WHERE employee_id IS NOT NULL;
GO


GO

/* ===================================================================== V4__master_data.sql */
PRINT N'V4__master_data';
GO
/* Flyway V4 — aligned with database/master-data.sql (no secrets) */
/*
  Master data (lookups, RBAC catalogue, SLA, workflow seeds, calendars).
  No passwords, connection strings, or LDAP secrets.
  SQL Server 2016 / 2019. Re-runnable: deletes seed rows that we control by code.
*/
SET NOCOUNT ON;
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
GO

GO

/* ---- departments ---- */
MERGE dbo.department AS t
USING (VALUES
    (N'IT', N'Information Technology'),
    (N'FIN', N'Finance'),
    (N'HR', N'Human Resources'),
    (N'OPS', N'Operations'),
    (N'CRD', N'Credit'),
    (N'COL', N'Collections'),
    (N'LGL', N'Legal'),
    (N'RSK', N'Risk'),
    (N'CMP', N'Compliance'),
    (N'ADM', N'Administration')
) AS s(code, name)
ON t.code = s.code
WHEN NOT MATCHED THEN INSERT (code, name) VALUES (s.code, s.name);
GO

MERGE dbo.role AS t
USING (VALUES
    (N'EMPLOYEE', N'Employee'),
    (N'MANAGER', N'Manager'),
    (N'HOD', N'HOD'),
    (N'CISO', N'CISO'),
    (N'IT_SERVICE_DESK', N'IT Service Desk'),
    (N'IT_IMPLEMENTOR', N'IT Implementor'),
    (N'IT_ADMIN', N'IT Admin'),
    (N'SYSTEM_ADMINISTRATOR', N'System Administrator')
) AS s(code, name)
ON t.code = s.code
WHEN NOT MATCHED THEN INSERT (code, name, is_system, is_active) VALUES (s.code, s.name, 1, 1);
GO

MERGE dbo.permission AS t
USING (VALUES
    (N'TICKET_CREATE', N'Create tickets'),
    (N'TICKET_VIEW_OWN', N'View own tickets'),
    (N'TICKET_VIEW_TEAM', N'View team tickets'),
    (N'TICKET_VIEW_DEPARTMENT', N'View department tickets'),
    (N'TICKET_VIEW_SECURITY', N'View security tickets'),
    (N'TICKET_VIEW_QUEUE_ALL', N'View service desk queue'),
    (N'TICKET_APPROVE_ASSIGNED_STAGE', N'Act on assigned workflow stage'),
    (N'TICKET_ASSIGN', N'Assign implementor'),
    (N'TICKET_FULFIL', N'Fulfil / resolve'),
    (N'SLA_MONITOR', N'Monitor SLA'),
    (N'REPORT_VIEW', N'View reports'),
    (N'KB_READ', N'Read knowledge base'),
    (N'AUDIT_VIEW', N'View audit log'),
    (N'ADMIN_USER_MANAGE', N'Manage portal users and roles'),
    (N'ADMIN_MASTERDATA_PROPOSE', N'Propose master-data changes'),
    (N'ADMIN_MASTERDATA_APPROVE', N'Approve master-data changes (checker)'),
    (N'ADMIN_SYSTEM', N'System settings (non-secret)'),
    (N'ASSET_MANAGE', N'Manage assets')
) AS s(code, description)
ON t.code = s.code
WHEN NOT MATCHED THEN INSERT (code, description) VALUES (s.code, s.description);
GO

/* Role-permission map: delete and re-seed system roles only */
DELETE rp
FROM dbo.role_permission rp
INNER JOIN dbo.role r ON r.role_id = rp.role_id
WHERE r.is_system = 1;

INSERT INTO dbo.role_permission (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM dbo.role r
CROSS JOIN dbo.permission p
WHERE r.code = N'EMPLOYEE'
  AND p.code IN (N'TICKET_CREATE', N'TICKET_VIEW_OWN', N'TICKET_APPROVE_ASSIGNED_STAGE', N'KB_READ');

INSERT INTO dbo.role_permission (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM dbo.role r
CROSS JOIN dbo.permission p
WHERE r.code = N'MANAGER'
  AND p.code IN (N'TICKET_CREATE', N'TICKET_VIEW_OWN', N'TICKET_VIEW_TEAM', N'TICKET_APPROVE_ASSIGNED_STAGE', N'REPORT_VIEW', N'KB_READ');

INSERT INTO dbo.role_permission (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM dbo.role r
CROSS JOIN dbo.permission p
WHERE r.code = N'HOD'
  AND p.code IN (N'TICKET_CREATE', N'TICKET_VIEW_OWN', N'TICKET_VIEW_DEPARTMENT', N'TICKET_APPROVE_ASSIGNED_STAGE', N'REPORT_VIEW', N'KB_READ');

INSERT INTO dbo.role_permission (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM dbo.role r
CROSS JOIN dbo.permission p
WHERE r.code = N'CISO'
  AND p.code IN (N'TICKET_CREATE', N'TICKET_VIEW_OWN', N'TICKET_VIEW_SECURITY', N'TICKET_APPROVE_ASSIGNED_STAGE', N'REPORT_VIEW', N'AUDIT_VIEW');

INSERT INTO dbo.role_permission (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM dbo.role r
CROSS JOIN dbo.permission p
WHERE r.code = N'IT_SERVICE_DESK'
  AND p.code IN (N'TICKET_CREATE', N'TICKET_VIEW_OWN', N'TICKET_VIEW_QUEUE_ALL', N'TICKET_ASSIGN', N'SLA_MONITOR', N'REPORT_VIEW', N'KB_READ');

INSERT INTO dbo.role_permission (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM dbo.role r
CROSS JOIN dbo.permission p
WHERE r.code = N'IT_IMPLEMENTOR'
  AND p.code IN (N'TICKET_CREATE', N'TICKET_VIEW_OWN', N'TICKET_FULFIL', N'KB_READ');

INSERT INTO dbo.role_permission (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM dbo.role r
CROSS JOIN dbo.permission p
WHERE r.code = N'IT_ADMIN'
  AND p.code IN (
    N'TICKET_CREATE', N'TICKET_VIEW_OWN', N'TICKET_VIEW_QUEUE_ALL', N'REPORT_VIEW',
    N'AUDIT_VIEW', N'ADMIN_USER_MANAGE', N'ADMIN_MASTERDATA_PROPOSE', N'ADMIN_MASTERDATA_APPROVE', N'ASSET_MANAGE');

INSERT INTO dbo.role_permission (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM dbo.role r
CROSS JOIN dbo.permission p
WHERE r.code = N'SYSTEM_ADMINISTRATOR';
GO

MERGE dbo.ticket_type AS t
USING (VALUES
    (N'INCIDENT', N'Incident', 10),
    (N'SERVICE_REQUEST', N'Service Request', 20),
    (N'ACCESS_REQUEST', N'Access Request', 30),
    (N'CHANGE_REQUEST', N'Change Request', 40),
    (N'SECURITY_INCIDENT', N'Security Incident', 50),
    (N'PROBLEM', N'Problem', 60),
    (N'HARDWARE_REQUEST', N'Hardware Request', 70),
    (N'SOFTWARE_REQUEST', N'Software Request', 80)
) AS s(code, name, sort_order)
ON t.code = s.code
WHEN NOT MATCHED THEN INSERT (code, name, sort_order) VALUES (s.code, s.name, s.sort_order);
GO

MERGE dbo.category AS t
USING (VALUES
    (N'HARDWARE', N'Hardware', 10),
    (N'SOFTWARE', N'Software', 20),
    (N'NETWORK', N'Network', 30),
    (N'EMAIL', N'Email', 40),
    (N'APPLICATION', N'Application', 50),
    (N'DATABASE', N'Database', 60),
    (N'ACCESS_MGMT', N'Access Management', 70),
    (N'CYBER_SECURITY', N'Cyber Security', 80),
    (N'INFRASTRUCTURE', N'Infrastructure', 90),
    (N'CLOUD', N'Cloud', 100),
    (N'TELEPHONY', N'Telephony', 110),
    (N'OTHER', N'Other', 120)
) AS s(code, name, sort_order)
ON t.code = s.code
WHEN NOT MATCHED THEN INSERT (code, name, sort_order) VALUES (s.code, s.name, s.sort_order);
GO

;WITH subs AS (
    SELECT c.category_id, v.code, v.name, v.sort_order
    FROM dbo.category c
    INNER JOIN (VALUES
        (N'HARDWARE', N'LAPTOP', N'Laptop Issue', 10),
        (N'HARDWARE', N'DESKTOP', N'Desktop Issue', 20),
        (N'HARDWARE', N'PRINTER', N'Printer Issue', 30),
        (N'HARDWARE', N'PERIPHERAL', N'Peripheral Request', 40),
        (N'HARDWARE', N'REPLACEMENT', N'Hardware Replacement', 50),
        (N'SOFTWARE', N'INSTALL', N'Software Installation', 10),
        (N'SOFTWARE', N'LICENSE', N'Software License', 20),
        (N'SOFTWARE', N'BUG', N'Software Bug', 30),
        (N'SOFTWARE', N'UPGRADE', N'Upgrade Request', 40),
        (N'NETWORK', N'VPN', N'VPN Access', 10),
        (N'NETWORK', N'WIFI', N'Wi-Fi Connectivity', 20),
        (N'NETWORK', N'LAN', N'LAN Connectivity', 30),
        (N'NETWORK', N'BW', N'Bandwidth Issue', 40),
        (N'EMAIL', N'MAILBOX', N'Mailbox Issue', 10),
        (N'EMAIL', N'DL', N'Distribution List', 20),
        (N'EMAIL', N'ACCESS', N'Email Access', 30),
        (N'EMAIL', N'PHISH', N'Spam/Phishing Report', 40),
        (N'APPLICATION', N'ERP', N'ERP Issue', 10),
        (N'APPLICATION', N'CRM', N'CRM Issue', 20),
        (N'APPLICATION', N'CBA', N'Core Banking Application', 30),
        (N'APPLICATION', N'PORTAL', N'Internal Portal Issue', 40),
        (N'DATABASE', N'DATA_ACCESS', N'Data Access Request', 10),
        (N'DATABASE', N'QUERY', N'Query Support', 20),
        (N'DATABASE', N'PERF', N'Database Performance', 30),
        (N'DATABASE', N'BACKUP', N'Backup/Restore', 40),
        (N'ACCESS_MGMT', N'NEW_USER', N'New User Access', 10),
        (N'ACCESS_MGMT', N'MODIFY', N'Access Modification', 20),
        (N'ACCESS_MGMT', N'REVOKE', N'Access Revocation', 30),
        (N'ACCESS_MGMT', N'PRIVILEGED', N'Privileged Access Request', 40),
        (N'ACCESS_MGMT', N'FILE_FOLDER', N'File/Folder Access Request', 50),
        (N'ACCESS_MGMT', N'NSHARE', N'Network Share Access', 60),
        (N'CYBER_SECURITY', N'PHISHING', N'Phishing Incident', 10),
        (N'CYBER_SECURITY', N'MALWARE', N'Malware/Virus', 20),
        (N'CYBER_SECURITY', N'LEAK', N'Data Leak Concern', 30),
        (N'CYBER_SECURITY', N'EXCEPTION', N'Security Policy Exception', 40),
        (N'INFRASTRUCTURE', N'SERVER', N'Server Issue', 10),
        (N'INFRASTRUCTURE', N'DC', N'Data Center Request', 20),
        (N'INFRASTRUCTURE', N'CLOUD_RES', N'Cloud Resource', 30),
        (N'INFRASTRUCTURE', N'UPS', N'UPS/Power', 40),
        (N'CLOUD', N'STORAGE', N'Cloud Storage', 10),
        (N'CLOUD', N'VM', N'Cloud VM Provisioning', 20),
        (N'CLOUD', N'SAAS', N'SaaS Subscription', 30),
        (N'CLOUD', N'ACCESS', N'Cloud Access', 40),
        (N'TELEPHONY', N'EXT', N'Extension Setup', 10),
        (N'TELEPHONY', N'BRIDGE', N'Conference Bridge', 20),
        (N'TELEPHONY', N'SIM', N'Mobile SIM/Device', 30),
        (N'OTHER', N'GENERAL', N'General Query', 10),
        (N'OTHER', N'MISC', N'Miscellaneous', 20)
    ) AS v(cat_code, code, name, sort_order) ON v.cat_code = c.code
)
MERGE dbo.sub_category AS t
USING subs AS s
ON t.category_id = s.category_id AND t.code = s.code
WHEN NOT MATCHED THEN INSERT (category_id, code, name, sort_order) VALUES (s.category_id, s.code, s.name, s.sort_order);
GO

MERGE dbo.sla_policy AS t
USING (VALUES
    (N'Critical', 15, 240, 0),
    (N'High', 30, 480, 0),
    (N'Medium', 120, 1440, 0),
    (N'Low', 240, 4320, 0)
) AS s(priority_code, response_minutes, resolution_minutes, is_24x7)
ON t.priority_code = s.priority_code
WHEN MATCHED THEN UPDATE SET response_minutes = s.response_minutes, resolution_minutes = s.resolution_minutes
WHEN NOT MATCHED THEN INSERT (priority_code, response_minutes, resolution_minutes, is_24x7)
     VALUES (s.priority_code, s.response_minutes, s.resolution_minutes, s.is_24x7);
GO

IF NOT EXISTS (SELECT 1 FROM dbo.ticket_number_config WHERE sequence_year = 2026)
    INSERT INTO dbo.ticket_number_config (prefix, include_year, padding, sequence_year, last_allocated)
    VALUES (N'ITSM', 1, 6, 2026, 1200);
GO

IF NOT EXISTS (SELECT 1 FROM dbo.attachment_policy)
    INSERT INTO dbo.attachment_policy (max_bytes, allowed_extensions, allowed_mime_types, virus_scan_required)
    VALUES (
        10485760,
        N'.pdf,.doc,.docx,.xls,.xlsx,.png,.jpg,.jpeg,.txt,.log,.msg',
        N'application/pdf,application/msword,application/vnd.openxmlformats-officedocument.wordprocessingml.document,image/png,image/jpeg,text/plain',
        0
    );
GO

MERGE dbo.business_calendar AS t
USING (VALUES
    (1, CAST(N'09:00:00' AS TIME(0)), CAST(N'18:00:00' AS TIME(0)), 1),
    (2, CAST(N'09:00:00' AS TIME(0)), CAST(N'18:00:00' AS TIME(0)), 1),
    (3, CAST(N'09:00:00' AS TIME(0)), CAST(N'18:00:00' AS TIME(0)), 1),
    (4, CAST(N'09:00:00' AS TIME(0)), CAST(N'18:00:00' AS TIME(0)), 1),
    (5, CAST(N'09:00:00' AS TIME(0)), CAST(N'18:00:00' AS TIME(0)), 1),
    (6, CAST(N'00:00:00' AS TIME(0)), CAST(N'00:00:00' AS TIME(0)), 0),
    (7, CAST(N'00:00:00' AS TIME(0)), CAST(N'00:00:00' AS TIME(0)), 0)
) AS s(weekday_iso, start_time, end_time, is_working_day)
ON t.weekday_iso = s.weekday_iso
WHEN MATCHED THEN UPDATE SET start_time = s.start_time, end_time = s.end_time, is_working_day = s.is_working_day
WHEN NOT MATCHED THEN INSERT (weekday_iso, start_time, end_time, timezone_id, is_working_day)
     VALUES (s.weekday_iso, s.start_time, s.end_time, N'India Standard Time', s.is_working_day);
GO

MERGE dbo.holiday AS t
USING (VALUES
    (CAST(N'2026-01-26' AS DATE), N'Republic Day'),
    (CAST(N'2026-08-15' AS DATE), N'Independence Day'),
    (CAST(N'2026-10-02' AS DATE), N'Gandhi Jayanti')
) AS s(holiday_date, name)
ON t.holiday_date = s.holiday_date
WHEN NOT MATCHED THEN INSERT (holiday_date, name, is_national) VALUES (s.holiday_date, s.name, 1);
GO

MERGE dbo.assignment_group AS t
USING (VALUES
    (N'IT_SERVICE_DESK', N'IT Service Desk'),
    (N'IT_IMPLEMENTORS', N'IT Implementors'),
    (N'IT_SECURITY_TEAM', N'IT Security Team')
) AS s(code, name)
ON t.code = s.code
WHEN NOT MATCHED THEN INSERT (code, name) VALUES (s.code, s.name);
GO

/* ---- Workflow definitions (seed templates; engine-driven, not Java if-else) ---- */
MERGE dbo.workflow_definition AS t
USING (VALUES
    (N'SR_CHAIN_TO_HOD_CISO_IMPL', N'Service Request — chain to HOD, CISO, Implementor', 1, N'Active',
        N'Default SR: expand LDAP manager hops until HOD, then CISO, then Implementor.'),
    (N'SR_CHAIN_TO_HOD_IMPL', N'Service Request — chain to HOD, Implementor (no CISO)', 1, N'Active',
        N'Optional SR clone without CISO. Bind via a rule on non-security categories.'),
    (N'INCIDENT_SD_THEN_IMPL', N'Incident — Service Desk then Implementor', 1, N'Active',
        N'Default Incident: SD triage/assign, then Implementor, requester confirmation, close.'),
    (N'INCIDENT_DIRECT_IMPL', N'Incident — direct to Implementor', 1, N'Inactive',
        N'Optional skip-SD template. Inactive until an admin activates a higher-priority rule.'),
    (N'SECURITY', N'Security / highly confidential', 1, N'Active',
        N'Manager chain to HOD, CISO, Implementor (same shape as default SR).'),
    (N'PRIVILEGED_ACCESS', N'Privileged access', 1, N'Active',
        N'Chain to HOD, CISO, IT Security Team, Implementor.')
) AS s(code, name, version_no, status_code, description)
ON t.code = s.code AND t.version_no = s.version_no
WHEN MATCHED THEN UPDATE SET name = s.name, status_code = s.status_code, description = s.description
WHEN NOT MATCHED THEN INSERT (code, name, version_no, status_code, description)
     VALUES (s.code, s.name, s.version_no, s.status_code, s.description);
GO

/* Replace stages/transitions/rules for seed codes (safe on empty or re-seed) */
DELETE wst
FROM dbo.workflow_stage_transition wst
INNER JOIN dbo.workflow_stage ws ON ws.workflow_stage_id = wst.workflow_stage_id
INNER JOIN dbo.workflow_definition wd ON wd.workflow_definition_id = ws.workflow_definition_id
WHERE wd.code IN (N'SR_CHAIN_TO_HOD_CISO_IMPL', N'SR_CHAIN_TO_HOD_IMPL', N'INCIDENT_SD_THEN_IMPL',
                  N'INCIDENT_DIRECT_IMPL', N'SECURITY', N'PRIVILEGED_ACCESS');

DELETE wr
FROM dbo.workflow_rule wr
INNER JOIN dbo.workflow_definition wd ON wd.workflow_definition_id = wr.workflow_definition_id
WHERE wd.code IN (N'SR_CHAIN_TO_HOD_CISO_IMPL', N'SR_CHAIN_TO_HOD_IMPL', N'INCIDENT_SD_THEN_IMPL',
                  N'INCIDENT_DIRECT_IMPL', N'SECURITY', N'PRIVILEGED_ACCESS')
  AND NOT EXISTS (SELECT 1 FROM dbo.workflow_instance wi WHERE wi.workflow_rule_id = wr.workflow_rule_id);

DELETE ws
FROM dbo.workflow_stage ws
INNER JOIN dbo.workflow_definition wd ON wd.workflow_definition_id = ws.workflow_definition_id
WHERE wd.code IN (N'SR_CHAIN_TO_HOD_CISO_IMPL', N'SR_CHAIN_TO_HOD_IMPL', N'INCIDENT_SD_THEN_IMPL',
                  N'INCIDENT_DIRECT_IMPL', N'SECURITY', N'PRIVILEGED_ACCESS')
  AND NOT EXISTS (
        SELECT 1 FROM dbo.workflow_instance_stage wis WHERE wis.workflow_stage_id = ws.workflow_stage_id);
GO

DECLARE @ciso BIGINT = (SELECT role_id FROM dbo.role WHERE code = N'CISO');
DECLARE @secGrp BIGINT = (SELECT assignment_group_id FROM dbo.assignment_group WHERE code = N'IT_SECURITY_TEAM');
DECLARE @sdGrp BIGINT = (SELECT assignment_group_id FROM dbo.assignment_group WHERE code = N'IT_SERVICE_DESK');
DECLARE @implGrp BIGINT = (SELECT assignment_group_id FROM dbo.assignment_group WHERE code = N'IT_IMPLEMENTORS');

DECLARE @sr BIGINT = (SELECT workflow_definition_id FROM dbo.workflow_definition WHERE code = N'SR_CHAIN_TO_HOD_CISO_IMPL' AND version_no = 1);
DECLARE @srNoCiso BIGINT = (SELECT workflow_definition_id FROM dbo.workflow_definition WHERE code = N'SR_CHAIN_TO_HOD_IMPL' AND version_no = 1);
DECLARE @inc BIGINT = (SELECT workflow_definition_id FROM dbo.workflow_definition WHERE code = N'INCIDENT_SD_THEN_IMPL' AND version_no = 1);
DECLARE @incDirect BIGINT = (SELECT workflow_definition_id FROM dbo.workflow_definition WHERE code = N'INCIDENT_DIRECT_IMPL' AND version_no = 1);
DECLARE @sec BIGINT = (SELECT workflow_definition_id FROM dbo.workflow_definition WHERE code = N'SECURITY' AND version_no = 1);
DECLARE @priv BIGINT = (SELECT workflow_definition_id FROM dbo.workflow_definition WHERE code = N'PRIVILEGED_ACCESS' AND version_no = 1);

IF NOT EXISTS (SELECT 1 FROM dbo.workflow_stage WHERE workflow_definition_id = @sr)
BEGIN
    INSERT INTO dbo.workflow_stage (workflow_definition_id, stage_order, code, label, stage_type, actor_strategy, role_id, assignment_group_id, send_back_target)
    VALUES
        (@sr, 10, N'hierarchy', N'Reporting hierarchy through HOD', N'APPROVAL', N'DYNAMIC_HIERARCHY_TO_HOD', NULL, NULL, N'PREVIOUS_STAGE'),
        (@sr, 20, N'ciso', N'CISO approval', N'APPROVAL', N'NAMED_ROLE', @ciso, NULL, N'PREVIOUS_STAGE'),
        (@sr, 30, N'implementor', N'Implementor', N'FULFILMENT', N'IMPLEMENTOR', NULL, @implGrp, NULL),
        (@sr, 40, N'confirmation', N'Requester confirmation', N'CONFIRMATION', N'REQUESTER', NULL, NULL, NULL),
        (@sr, 50, N'closed', N'Closed', N'CLOSURE', N'SYSTEM', NULL, NULL, NULL);
END

IF NOT EXISTS (SELECT 1 FROM dbo.workflow_stage WHERE workflow_definition_id = @srNoCiso)
BEGIN
    INSERT INTO dbo.workflow_stage (workflow_definition_id, stage_order, code, label, stage_type, actor_strategy, role_id, assignment_group_id, send_back_target)
    VALUES
        (@srNoCiso, 10, N'hierarchy', N'Reporting hierarchy through HOD', N'APPROVAL', N'DYNAMIC_HIERARCHY_TO_HOD', NULL, NULL, N'PREVIOUS_STAGE'),
        (@srNoCiso, 20, N'implementor', N'Implementor', N'FULFILMENT', N'IMPLEMENTOR', NULL, @implGrp, NULL),
        (@srNoCiso, 30, N'confirmation', N'Requester confirmation', N'CONFIRMATION', N'REQUESTER', NULL, NULL, NULL),
        (@srNoCiso, 40, N'closed', N'Closed', N'CLOSURE', N'SYSTEM', NULL, NULL, NULL);
END

IF NOT EXISTS (SELECT 1 FROM dbo.workflow_stage WHERE workflow_definition_id = @inc)
BEGIN
    INSERT INTO dbo.workflow_stage (workflow_definition_id, stage_order, code, label, stage_type, actor_strategy, role_id, assignment_group_id, send_back_target)
    VALUES
        (@inc, 10, N'servicedesk', N'IT Service Desk triage', N'ASSIGNMENT', N'SERVICE_DESK', NULL, @sdGrp, NULL),
        (@inc, 20, N'implementor', N'Implementor', N'FULFILMENT', N'IMPLEMENTOR', NULL, @implGrp, NULL),
        (@inc, 30, N'confirmation', N'Requester confirmation', N'CONFIRMATION', N'REQUESTER', NULL, NULL, NULL),
        (@inc, 40, N'closed', N'Closed', N'CLOSURE', N'SYSTEM', NULL, NULL, NULL);
END

IF NOT EXISTS (SELECT 1 FROM dbo.workflow_stage WHERE workflow_definition_id = @incDirect)
BEGIN
    INSERT INTO dbo.workflow_stage (workflow_definition_id, stage_order, code, label, stage_type, actor_strategy, role_id, assignment_group_id, send_back_target)
    VALUES
        (@incDirect, 10, N'implementor', N'Implementor', N'FULFILMENT', N'IMPLEMENTOR', NULL, @implGrp, NULL),
        (@incDirect, 20, N'confirmation', N'Requester confirmation', N'CONFIRMATION', N'REQUESTER', NULL, NULL, NULL),
        (@incDirect, 30, N'closed', N'Closed', N'CLOSURE', N'SYSTEM', NULL, NULL, NULL);
END

IF NOT EXISTS (SELECT 1 FROM dbo.workflow_stage WHERE workflow_definition_id = @sec)
BEGIN
    INSERT INTO dbo.workflow_stage (workflow_definition_id, stage_order, code, label, stage_type, actor_strategy, role_id, assignment_group_id, send_back_target)
    VALUES
        (@sec, 10, N'hierarchy', N'Reporting hierarchy through HOD', N'APPROVAL', N'DYNAMIC_HIERARCHY_TO_HOD', NULL, NULL, N'PREVIOUS_STAGE'),
        (@sec, 20, N'ciso', N'CISO approval', N'APPROVAL', N'NAMED_ROLE', @ciso, NULL, N'PREVIOUS_STAGE'),
        (@sec, 30, N'implementor', N'Implementor', N'FULFILMENT', N'IMPLEMENTOR', NULL, @implGrp, NULL),
        (@sec, 40, N'confirmation', N'Requester confirmation', N'CONFIRMATION', N'REQUESTER', NULL, NULL, NULL),
        (@sec, 50, N'closed', N'Closed', N'CLOSURE', N'SYSTEM', NULL, NULL, NULL);
END

IF NOT EXISTS (SELECT 1 FROM dbo.workflow_stage WHERE workflow_definition_id = @priv)
BEGIN
    INSERT INTO dbo.workflow_stage (workflow_definition_id, stage_order, code, label, stage_type, actor_strategy, role_id, assignment_group_id, send_back_target)
    VALUES
        (@priv, 10, N'hierarchy', N'Reporting hierarchy through HOD', N'APPROVAL', N'DYNAMIC_HIERARCHY_TO_HOD', NULL, NULL, N'PREVIOUS_STAGE'),
        (@priv, 20, N'ciso', N'CISO approval', N'APPROVAL', N'NAMED_ROLE', @ciso, NULL, N'PREVIOUS_STAGE'),
        (@priv, 30, N'security_team', N'IT Security Team', N'ASSIGNMENT', N'ASSIGNMENT_GROUP', NULL, @secGrp, NULL),
        (@priv, 40, N'implementor', N'Implementor', N'FULFILMENT', N'IMPLEMENTOR', NULL, @implGrp, NULL),
        (@priv, 50, N'confirmation', N'Requester confirmation', N'CONFIRMATION', N'REQUESTER', NULL, NULL, NULL),
        (@priv, 60, N'closed', N'Closed', N'CLOSURE', N'SYSTEM', NULL, NULL, NULL);
END
GO

INSERT INTO dbo.workflow_stage_transition (workflow_stage_id, action_code, remarks_required)
SELECT ws.workflow_stage_id, a.action_code, a.remarks_required
FROM dbo.workflow_stage ws
INNER JOIN dbo.workflow_definition wd ON wd.workflow_definition_id = ws.workflow_definition_id
CROSS JOIN (VALUES
    (N'APPROVAL', N'APPROVE', 1),
    (N'APPROVAL', N'REJECT', 1),
    (N'APPROVAL', N'SEND_BACK', 1),
    (N'ASSIGNMENT', N'ASSIGN', 0),
    (N'ASSIGNMENT', N'REASSIGN', 0),
    (N'FULFILMENT', N'ACCEPT', 0),
    (N'FULFILMENT', N'START', 0),
    (N'FULFILMENT', N'HOLD', 0),
    (N'FULFILMENT', N'RESOLVE', 0),
    (N'FULFILMENT', N'REASSIGN', 0),
    (N'CONFIRMATION', N'APPROVE', 1),
    (N'CONFIRMATION', N'SEND_BACK', 1),
    (N'CLOSURE', N'COMPLETE', 0)
) AS a(stage_type, action_code, remarks_required)
WHERE wd.code IN (N'SR_CHAIN_TO_HOD_CISO_IMPL', N'SR_CHAIN_TO_HOD_IMPL', N'INCIDENT_SD_THEN_IMPL',
                  N'INCIDENT_DIRECT_IMPL', N'SECURITY', N'PRIVILEGED_ACCESS')
  AND ws.stage_type = a.stage_type
  AND NOT EXISTS (
        SELECT 1 FROM dbo.workflow_stage_transition x
        WHERE x.workflow_stage_id = ws.workflow_stage_id AND x.action_code = a.action_code);
GO

/* Rule matrix: lower priority number wins. No rule for INCIDENT_DIRECT_IMPL (inactive). */
INSERT INTO dbo.workflow_rule (name, priority, status_code, condition_json, workflow_definition_id)
SELECT s.name, s.priority, s.status_code, s.condition_json, wd.workflow_definition_id
FROM (VALUES
    (N'Privileged access requests', 10, N'Active', N'{"sub_category":["Privileged Access Request"]}'),
    (N'Cyber Security category', 20, N'Active', N'{"category":["Cyber Security"]}'),
    (N'Security Incident ticket type', 21, N'Active', N'{"ticket_type":["Security Incident"]}'),
    (N'Highly Confidential requests', 22, N'Active', N'{"confidentiality":["Highly Confidential"]}'),
    (N'Incident — Service Desk then Implementor', 30, N'Active', N'{"ticket_type":["Incident"]}'),
    (N'Service Request default (CISO on template)', 40, N'Active', N'{"ticket_type":["Service Request"]}'),
    (N'Catch-all fallback', 999, N'Active', N'{}')
) AS s(name, priority, status_code, condition_json)
INNER JOIN dbo.workflow_definition wd ON wd.version_no = 1 AND (
    (s.priority IN (10) AND wd.code = N'PRIVILEGED_ACCESS')
    OR (s.priority IN (20, 21, 22) AND wd.code = N'SECURITY')
    OR (s.priority = 30 AND wd.code = N'INCIDENT_SD_THEN_IMPL')
    OR (s.priority IN (40, 999) AND wd.code = N'SR_CHAIN_TO_HOD_CISO_IMPL')
)
WHERE NOT EXISTS (SELECT 1 FROM dbo.workflow_rule r WHERE r.priority = s.priority AND r.status_code = N'Active');
GO

MERGE dbo.kb_category AS t
USING (VALUES
    (N'VPN', N'VPN'), (N'EMAIL', N'Email'), (N'PASSWORD', N'Password'), (N'NETWORK', N'Network'),
    (N'LAPTOP', N'Laptop'), (N'APPLICATION', N'Application'), (N'SECURITY', N'Security'),
    (N'OFFICE', N'Microsoft Office'), (N'ERP', N'ERP'), (N'LMS', N'LMS'), (N'OTHER', N'Other')
) AS s(code, name)
ON t.code = s.code
WHEN NOT MATCHED THEN INSERT (code, name) VALUES (s.code, s.name);
GO

IF NOT EXISTS (SELECT 1 FROM dbo.kb_article)
BEGIN
    INSERT INTO dbo.kb_article (kb_category_id, title, body, is_published)
    SELECT kb_category_id,
           N'How to connect to corporate VPN',
           N'Step 1: Install GlobalProtect from the software centre. Step 2: Use your domain credentials. Do not share OTP. Raise an ITSM ticket if connection fails after 15 minutes.',
           1
    FROM dbo.kb_category WHERE code = N'VPN';
END
GO

MERGE dbo.notification_rule AS t
USING (VALUES
    (N'TICKET_CREATED', 1, 1),
    (N'APPROVAL_REQUIRED', 1, 1),
    (N'APPROVED', 1, 1),
    (N'REJECTED', 1, 1),
    (N'SLA_NEAR', 1, 1),
    (N'SLA_BREACHED', 1, 1),
    (N'RESOLVED', 1, 1),
    (N'ASSIGNED', 1, 1)
) AS s(event_code, in_app, send_email)
ON t.event_code = s.event_code
WHEN NOT MATCHED THEN INSERT (event_code, in_app, send_email, is_active)
     VALUES (s.event_code, s.in_app, s.send_email, 1);
GO

MERGE dbo.email_template AS t
USING (VALUES
    (N'TICKET_CREATED', N'[ITSM] Ticket {{ticketNumber}} created', N'Your request {{ticketNumber}} has been submitted.'),
    (N'APPROVAL_REQUIRED', N'[ITSM] Approval required {{ticketNumber}}', N'Please approve or reject {{ticketNumber}}. Remarks are mandatory.'),
    (N'RESOLVED', N'[ITSM] Ticket {{ticketNumber}} resolved', N'Please confirm resolution of {{ticketNumber}}.')
) AS s(code, subject, body)
ON t.code = s.code
WHEN NOT MATCHED THEN INSERT (code, subject, body) VALUES (s.code, s.subject, s.body);
GO

MERGE dbo.system_setting AS t
USING (VALUES
    (N'sla.amber-percent', N'20', N'sla', N'Percent remaining when SLA turns amber'),
    (N'workflow.max-manager-hops', N'12', N'workflow', N'Cap for DYNAMIC_HIERARCHY_TO_HOD expansion'),
    (N'approval.remarks-min-length', N'10', N'workflow', N'Assumed minimum remarks length (Q34)'),
    (N'ui.timezone', N'India Standard Time', N'ui', N'Display time zone')
) AS s(setting_key, setting_value, category, description)
ON t.setting_key = s.setting_key
WHEN MATCHED THEN UPDATE SET setting_value = s.setting_value, description = s.description
WHEN NOT MATCHED THEN INSERT (setting_key, setting_value, category, description, is_secret)
     VALUES (s.setting_key, s.setting_value, s.category, s.description, 0);
GO

IF NOT EXISTS (SELECT 1 FROM dbo.escalation_matrix)
BEGIN
    INSERT INTO dbo.escalation_matrix (trigger_code, priority_code, notify_role_id, notify_group_id)
    SELECT N'NEAR', N'Critical', NULL, assignment_group_id FROM dbo.assignment_group WHERE code = N'IT_SERVICE_DESK'
    UNION ALL
    SELECT N'BREACHED', N'Critical', role_id, NULL FROM dbo.role WHERE code = N'HOD'
    UNION ALL
    SELECT N'BREACHED', N'High', NULL, assignment_group_id FROM dbo.assignment_group WHERE code = N'IT_SERVICE_DESK';
END
GO


GO

/* ===================================================================== V5__database_roles.sql */
PRINT N'V5__database_roles';
GO
/* Flyway V5 — aligned with database/security-data.sql (roles/grants, no logins) */
/*
  Database roles and grants. Does NOT create SQL logins or passwords.
  Map an instance login (created outside Git) with:
    ALTER ROLE itsm_app ADD MEMBER [your_app_login];
  SQL Server 2016 / 2019.
*/
SET NOCOUNT ON;
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
GO

GO

IF NOT EXISTS (SELECT 1 FROM sys.database_principals WHERE name = N'itsm_app' AND type = N'R')
    CREATE ROLE [itsm_app] AUTHORIZATION [dbo];
IF NOT EXISTS (SELECT 1 FROM sys.database_principals WHERE name = N'itsm_readonly' AND type = N'R')
    CREATE ROLE [itsm_readonly] AUTHORIZATION [dbo];
IF NOT EXISTS (SELECT 1 FROM sys.database_principals WHERE name = N'itsm_auditor' AND type = N'R')
    CREATE ROLE [itsm_auditor] AUTHORIZATION [dbo];
GO

/* Application: DML on operational tables; INSERT-only on audit_log */
DECLARE @sql NVARCHAR(MAX);
DECLARE @t SYSNAME;

DECLARE c CURSOR LOCAL FAST_FORWARD FOR
    SELECT t.name
    FROM sys.tables t
    INNER JOIN sys.schemas s ON s.schema_id = t.schema_id
    WHERE s.name = N'dbo' AND t.name <> N'audit_log';

OPEN c;
FETCH NEXT FROM c INTO @t;
WHILE @@FETCH_STATUS = 0
BEGIN
    SET @sql = N'GRANT SELECT, INSERT, UPDATE, DELETE ON dbo.' + QUOTENAME(@t) + N' TO [itsm_app];';
    EXEC(@sql);
    SET @sql = N'GRANT SELECT ON dbo.' + QUOTENAME(@t) + N' TO [itsm_readonly];';
    EXEC(@sql);
    SET @sql = N'GRANT SELECT ON dbo.' + QUOTENAME(@t) + N' TO [itsm_auditor];';
    EXEC(@sql);
    FETCH NEXT FROM c INTO @t;
END
CLOSE c;
DEALLOCATE c;
GO

GRANT SELECT, INSERT ON dbo.audit_log TO [itsm_app];
DENY UPDATE, DELETE ON dbo.audit_log TO [itsm_app];
GRANT SELECT ON dbo.audit_log TO [itsm_readonly];
DENY INSERT, UPDATE, DELETE ON dbo.audit_log TO [itsm_readonly];
GRANT SELECT ON dbo.audit_log TO [itsm_auditor];
DENY INSERT, UPDATE, DELETE ON dbo.audit_log TO [itsm_auditor];
GO

REVOKE ALTER, CONTROL ON dbo.audit_log FROM [itsm_app];
GO


GO

/* ===================================================================== V7__restore_default_workflow_rules.sql */
PRINT N'V7__restore_default_workflow_rules';
GO
/*
  Flyway V7 — repair dbo.workflow_rule and restore the default workflow rule matrix.
  SQL Server 2016 / 2019 (compat 130). (V6 is reserved for db/dev sample data.)

  Why: without an Active rule every submit fails with "No active workflow rule matches this
  request". On one database the table had also been rebuilt outside Flyway without IDENTITY on
  workflow_rule_id, so inserts failed with "Cannot insert the value NULL into column
  'workflow_rule_id'".

  Step 1 rebuilds the table exactly as in V1 (+ V3 index), but ONLY when workflow_rule_id is not
  an IDENTITY column AND the table is empty; if it has rows the migration stops with an error
  instead of risking data loss. Healthy databases skip step 1 entirely.
  Step 2 inserts each default rule only when no Active rule holds its priority, so rules an
  administrator has edited are left alone.
*/
SET NOCOUNT ON;
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
GO

IF COLUMNPROPERTY(OBJECT_ID(N'dbo.workflow_rule'), N'workflow_rule_id', 'IsIdentity') = 0
BEGIN
    IF EXISTS (SELECT 1 FROM dbo.workflow_rule)
        THROW 50007, N'V7: dbo.workflow_rule.workflow_rule_id is not an IDENTITY column and the table has rows. Repair it manually (restore the V1 definition) and restart.', 1;

    /* Drop every foreign key that points at workflow_rule (normally FK_wfi_rule on workflow_instance). */
    DECLARE @dropFk NVARCHAR(MAX) = N'';
    SELECT @dropFk = @dropFk + N'ALTER TABLE ' + QUOTENAME(OBJECT_SCHEMA_NAME(fk.parent_object_id)) + N'.'
                   + QUOTENAME(OBJECT_NAME(fk.parent_object_id)) + N' DROP CONSTRAINT ' + QUOTENAME(fk.name) + N';'
    FROM sys.foreign_keys fk
    WHERE fk.referenced_object_id = OBJECT_ID(N'dbo.workflow_rule');
    EXEC sys.sp_executesql @dropFk;

    DROP TABLE dbo.workflow_rule;

    CREATE TABLE dbo.workflow_rule (
        workflow_rule_id         BIGINT IDENTITY(1,1) NOT NULL,
        name                     NVARCHAR(128) NOT NULL,
        priority                 INT NOT NULL,
        status_code              NVARCHAR(32) NOT NULL,
        condition_json           NVARCHAR(MAX) NOT NULL,
        workflow_definition_id   BIGINT NOT NULL,
        CONSTRAINT PK_workflow_rule PRIMARY KEY CLUSTERED (workflow_rule_id),
        CONSTRAINT FK_workflow_rule_def FOREIGN KEY (workflow_definition_id) REFERENCES dbo.workflow_definition (workflow_definition_id),
        CONSTRAINT CK_workflow_rule_status CHECK (status_code IN (N'Active', N'Inactive', N'PendingChecker'))
    );

    /* Dynamic SQL so these compile against the NEW table, not the one dropped above
       (and so ISJSON, SQL Server 2016+, is never parsed on 2012/2014). */
    IF CAST(PARSENAME(CAST(SERVERPROPERTY('ProductVersion') AS NVARCHAR(128)), 4) AS INT) >= 13
        EXEC sys.sp_executesql N'ALTER TABLE dbo.workflow_rule ADD CONSTRAINT CK_workflow_rule_json CHECK (ISJSON(condition_json) = 1);';

    EXEC sys.sp_executesql N'CREATE UNIQUE NONCLUSTERED INDEX UQ_workflow_rule_active_priority
        ON dbo.workflow_rule (priority) WHERE status_code = N''Active'';';

    IF COL_LENGTH(N'dbo.workflow_instance', N'workflow_rule_id') IS NOT NULL
        EXEC sys.sp_executesql N'ALTER TABLE dbo.workflow_instance
            ADD CONSTRAINT FK_wfi_rule FOREIGN KEY (workflow_rule_id) REFERENCES dbo.workflow_rule (workflow_rule_id);';
END
GO

INSERT INTO dbo.workflow_rule (name, priority, status_code, condition_json, workflow_definition_id)
SELECT s.name, s.priority, s.status_code, s.condition_json, wd.workflow_definition_id
FROM (VALUES
    (N'Privileged access requests', 10, N'Active', N'{"sub_category":["Privileged Access Request"]}'),
    (N'Cyber Security category', 20, N'Active', N'{"category":["Cyber Security"]}'),
    (N'Security Incident ticket type', 21, N'Active', N'{"ticket_type":["Security Incident"]}'),
    (N'Highly Confidential requests', 22, N'Active', N'{"confidentiality":["Highly Confidential"]}'),
    (N'Incident — Service Desk then Implementor', 30, N'Active', N'{"ticket_type":["Incident"]}'),
    (N'Service Request default (CISO on template)', 40, N'Active', N'{"ticket_type":["Service Request"]}'),
    (N'Catch-all fallback', 999, N'Active', N'{}')
) AS s(name, priority, status_code, condition_json)
INNER JOIN dbo.workflow_definition wd ON wd.version_no = 1 AND wd.status_code = N'Active' AND (
    (s.priority IN (10) AND wd.code = N'PRIVILEGED_ACCESS')
    OR (s.priority IN (20, 21, 22) AND wd.code = N'SECURITY')
    OR (s.priority = 30 AND wd.code = N'INCIDENT_SD_THEN_IMPL')
    OR (s.priority IN (40, 999) AND wd.code = N'SR_CHAIN_TO_HOD_CISO_IMPL')
)
WHERE NOT EXISTS (SELECT 1 FROM dbo.workflow_rule r WHERE r.priority = s.priority AND r.status_code = N'Active');
GO

GO

/* ===================================================================== V8__notification_nvarchar.sql */
PRINT N'V8__notification_nvarchar';
GO
/*
  Flyway V8 — dbo.notification text columns back to NVARCHAR (as defined in V1).
  SQL Server 2016 / 2019 (compat 130).

  In-app notifications are now written by the application (entity Notification, @Nationalized).
  On one database the table had been rebuilt outside Flyway with VARCHAR columns, which fails
  Hibernate schema validation and cannot store non-Latin names. Each column is altered only when
  it is currently VARCHAR, so databases built from V1 are untouched. Neither column is part of an
  index or constraint, so ALTER COLUMN is enough.
*/
SET NOCOUNT ON;
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
GO

IF EXISTS (SELECT 1 FROM sys.columns c INNER JOIN sys.types t ON t.user_type_id = c.user_type_id
           WHERE c.object_id = OBJECT_ID(N'dbo.notification') AND c.name = N'title' AND t.name = N'varchar')
    ALTER TABLE dbo.notification ALTER COLUMN title NVARCHAR(256) NOT NULL;
GO

IF EXISTS (SELECT 1 FROM sys.columns c INNER JOIN sys.types t ON t.user_type_id = c.user_type_id
           WHERE c.object_id = OBJECT_ID(N'dbo.notification') AND c.name = N'body' AND t.name = N'varchar')
    ALTER TABLE dbo.notification ALTER COLUMN body NVARCHAR(1000) NOT NULL;
GO

GO

/* ===================================================================== V9__ad_account_unlock_permission.sql */
PRINT N'V9__ad_account_unlock_permission';
GO
-- AD Account Unlock page: new permission for IT Service Desk and System Administrator.
-- Idempotent: inserts only what is missing.

IF NOT EXISTS (SELECT 1 FROM dbo.permission WHERE code = N'AD_ACCOUNT_UNLOCK')
    INSERT INTO dbo.permission (code, description)
    VALUES (N'AD_ACCOUNT_UNLOCK', N'Unlock locked Active Directory accounts');

INSERT INTO dbo.role_permission (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM dbo.role r
CROSS JOIN dbo.permission p
WHERE r.code IN (N'IT_SERVICE_DESK', N'SYSTEM_ADMINISTRATOR')
  AND p.code = N'AD_ACCOUNT_UNLOCK'
  AND NOT EXISTS (SELECT 1 FROM dbo.role_permission rp
                  WHERE rp.role_id = r.role_id AND rp.permission_id = p.permission_id);

GO

/* ===================================================================== afterMigrate.sql */
/*
  Flyway callback: runs on the migration connection after every migrate.
  The versioned scripts start with SET NOCOUNT ON. If that connection is ever handed back to the
  application pool, NOCOUNT ON makes UPDATEs report -1 rows and Hibernate then fails with
  "optimistic locking failed". Turn it off again before the connection is released.
*/
SET NOCOUNT OFF;

GO
PRINT N'ITSM Portal schema installed.';
SET NOEXEC OFF;
GO
