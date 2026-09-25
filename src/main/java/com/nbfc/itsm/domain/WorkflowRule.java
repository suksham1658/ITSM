package com.nbfc.itsm.domain;

import org.hibernate.annotations.Nationalized;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

@Entity
@Table(name = "workflow_rule")
public class WorkflowRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "workflow_rule_id")
    private Long workflowRuleId;

    // NVARCHAR columns (V1 / V7): @Nationalized so ddl-auto=validate expects nvarchar, not varchar.
    @Nationalized
    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "priority", nullable = false)
    private int priority;

    @Nationalized
    @Column(name = "status_code", nullable = false, length = 32)
    private String statusCode;

    @Nationalized
    @Column(name = "condition_json", nullable = false, columnDefinition = "nvarchar(max)")
    private String conditionJson;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workflow_definition_id", nullable = false)
    private WorkflowDefinition workflowDefinition;

    public Long getWorkflowRuleId() {
        return workflowRuleId;
    }

    public void setWorkflowRuleId(Long workflowRuleId) {
        this.workflowRuleId = workflowRuleId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public String getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(String statusCode) {
        this.statusCode = statusCode;
    }

    public String getConditionJson() {
        return conditionJson;
    }

    public void setConditionJson(String conditionJson) {
        this.conditionJson = conditionJson;
    }

    public WorkflowDefinition getWorkflowDefinition() {
        return workflowDefinition;
    }

    public void setWorkflowDefinition(WorkflowDefinition workflowDefinition) {
        this.workflowDefinition = workflowDefinition;
    }
}
