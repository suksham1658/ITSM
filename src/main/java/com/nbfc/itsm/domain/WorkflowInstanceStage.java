package com.nbfc.itsm.domain;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "workflow_instance_stage")
public class WorkflowInstanceStage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "workflow_instance_stage_id")
    private Long workflowInstanceStageId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workflow_instance_id", nullable = false)
    private WorkflowInstance workflowInstance;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workflow_stage_id")
    private WorkflowStage workflowStage;

    @Column(name = "stage_order", nullable = false)
    private int stageOrder;

    @Column(name = "code", nullable = false, length = 64)
    private String code;

    @Column(name = "label", nullable = false, length = 128)
    private String label;

    @Column(name = "stage_type", nullable = false, length = 32)
    private String stageType;

    @Column(name = "actor_strategy", nullable = false, length = 64)
    private String actorStrategy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resolved_employee_id")
    private Employee resolvedEmployee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resolved_role_id")
    private Role resolvedRole;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resolved_group_id")
    private AssignmentGroup resolvedGroup;

    /**
     * Fulfilment step sent by the IT Service Desk to several implementors at once: only these people may
     * pick it up, and the first to Accept / Start owns it ({@link #getResolvedEmployee()}).
     */
    @javax.persistence.ElementCollection(fetch = FetchType.EAGER)
    @javax.persistence.CollectionTable(name = "workflow_instance_stage_assignee",
            joinColumns = @JoinColumn(name = "workflow_instance_stage_id"))
    @Column(name = "employee_id")
    private java.util.Set<Long> assigneeIds = new java.util.LinkedHashSet<Long>();

    public java.util.Set<Long> getAssigneeIds() {
        return assigneeIds;
    }

    public void setAssigneeIds(java.util.Set<Long> assigneeIds) {
        this.assigneeIds = assigneeIds;
    }

    @Column(name = "status_code", nullable = false, length = 32)
    private String statusCode;

    @Column(name = "action_code", length = 32)
    private String actionCode;

    @Column(name = "remarks", length = 2000)
    private String remarks;

    @Column(name = "acted_at_utc")
    private Instant actedAtUtc;

    public Long getWorkflowInstanceStageId() {
        return workflowInstanceStageId;
    }

    public void setWorkflowInstanceStageId(Long workflowInstanceStageId) {
        this.workflowInstanceStageId = workflowInstanceStageId;
    }

    public WorkflowInstance getWorkflowInstance() {
        return workflowInstance;
    }

    public void setWorkflowInstance(WorkflowInstance workflowInstance) {
        this.workflowInstance = workflowInstance;
    }

    public WorkflowStage getWorkflowStage() {
        return workflowStage;
    }

    public void setWorkflowStage(WorkflowStage workflowStage) {
        this.workflowStage = workflowStage;
    }

    public int getStageOrder() {
        return stageOrder;
    }

    public void setStageOrder(int stageOrder) {
        this.stageOrder = stageOrder;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getStageType() {
        return stageType;
    }

    public void setStageType(String stageType) {
        this.stageType = stageType;
    }

    public String getActorStrategy() {
        return actorStrategy;
    }

    public void setActorStrategy(String actorStrategy) {
        this.actorStrategy = actorStrategy;
    }

    public Employee getResolvedEmployee() {
        return resolvedEmployee;
    }

    public void setResolvedEmployee(Employee resolvedEmployee) {
        this.resolvedEmployee = resolvedEmployee;
    }

    public Role getResolvedRole() {
        return resolvedRole;
    }

    public void setResolvedRole(Role resolvedRole) {
        this.resolvedRole = resolvedRole;
    }

    public AssignmentGroup getResolvedGroup() {
        return resolvedGroup;
    }

    public void setResolvedGroup(AssignmentGroup resolvedGroup) {
        this.resolvedGroup = resolvedGroup;
    }

    public String getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(String statusCode) {
        this.statusCode = statusCode;
    }

    public String getActionCode() {
        return actionCode;
    }

    public void setActionCode(String actionCode) {
        this.actionCode = actionCode;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }

    public Instant getActedAtUtc() {
        return actedAtUtc;
    }

    public void setActedAtUtc(Instant actedAtUtc) {
        this.actedAtUtc = actedAtUtc;
    }
}
