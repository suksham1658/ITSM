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
