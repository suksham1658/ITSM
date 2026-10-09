package com.nbfc.itsm.ticket;

import com.nbfc.itsm.domain.Ticket;
import com.nbfc.itsm.domain.TicketAttachment;
import com.nbfc.itsm.domain.TicketComment;
import com.nbfc.itsm.domain.TicketSla;
import com.nbfc.itsm.domain.WorkflowInstance;
import com.nbfc.itsm.domain.WorkflowInstanceStage;
import com.nbfc.itsm.domain.WorkflowStageTransition;

import java.util.ArrayList;
import java.util.List;

public class TicketDetail {

    private Ticket ticket;
    private WorkflowInstance instance;
    private List<WorkflowInstanceStage> stages = new ArrayList<WorkflowInstanceStage>();
    private List<TicketComment> comments = new ArrayList<TicketComment>();
    private List<TicketAttachment> attachments = new ArrayList<TicketAttachment>();
    private TicketSla sla;
    private WorkflowInstanceStage current;
    private List<WorkflowStageTransition> allowed = new ArrayList<WorkflowStageTransition>();
    private boolean canAct;
    private boolean canCommentInternal;
    private boolean showInternalComments;
    /** Who assigned / reassigned the ticket to whom, oldest first. */
    private List<com.nbfc.itsm.domain.TicketAssignmentLog> assignmentLog = new ArrayList<com.nbfc.itsm.domain.TicketAssignmentLog>();
    /** The requester's directory details are shown to everyone working on the ticket, not to the requester. */
    private boolean showRequesterDetails;
    /** Waiting for the requester: when it closes on its own if they do not answer. */
    private java.time.Instant autoCloseAt;
    /** Closed automatically: until when the requester may re-open it (null = not re-openable). */
    private java.time.Instant reopenUntil;
    /** The viewer is the requester and the re-open period is still running. */
    private boolean canReopen;
    /** The ticket is Closed but still within its re-open window (closed, not yet final) — for any viewer. */
    private boolean reopenWindowOpen;

    public boolean isReopenWindowOpen() { return reopenWindowOpen; }
    public void setReopenWindowOpen(boolean reopenWindowOpen) { this.reopenWindowOpen = reopenWindowOpen; }
    /** IT Service Desk / System Administrator may change the priority (SLA recalculated). */
    private boolean canChangePriority;
    /** Which workflow this ticket is following (read inside the transaction; the association is lazy). */
    private String workflowName;
    private String workflowCode;
    private Integer workflowVersion;
    private String workflowStatus;
    private Long workflowDefinitionId;

    public String getWorkflowName() { return workflowName; }
    public void setWorkflowName(String workflowName) { this.workflowName = workflowName; }
    public String getWorkflowCode() { return workflowCode; }
    public void setWorkflowCode(String workflowCode) { this.workflowCode = workflowCode; }
    public Integer getWorkflowVersion() { return workflowVersion; }
    public void setWorkflowVersion(Integer workflowVersion) { this.workflowVersion = workflowVersion; }
    public String getWorkflowStatus() { return workflowStatus; }
    public void setWorkflowStatus(String workflowStatus) { this.workflowStatus = workflowStatus; }
    public Long getWorkflowDefinitionId() { return workflowDefinitionId; }
    public void setWorkflowDefinitionId(Long workflowDefinitionId) { this.workflowDefinitionId = workflowDefinitionId; }

    public boolean isCanChangePriority() {
        return canChangePriority;
    }

    public void setCanChangePriority(boolean canChangePriority) {
        this.canChangePriority = canChangePriority;
    }

    public java.time.Instant getAutoCloseAt() {
        return autoCloseAt;
    }

    public void setAutoCloseAt(java.time.Instant autoCloseAt) {
        this.autoCloseAt = autoCloseAt;
    }

    public java.time.Instant getReopenUntil() {
        return reopenUntil;
    }

    public void setReopenUntil(java.time.Instant reopenUntil) {
        this.reopenUntil = reopenUntil;
    }

    public boolean isCanReopen() {
        return canReopen;
    }

    public void setCanReopen(boolean canReopen) {
        this.canReopen = canReopen;
    }

    public List<com.nbfc.itsm.domain.TicketAssignmentLog> getAssignmentLog() {
        return assignmentLog;
    }

    public void setAssignmentLog(List<com.nbfc.itsm.domain.TicketAssignmentLog> assignmentLog) {
        this.assignmentLog = assignmentLog;
    }

    public boolean isShowRequesterDetails() {
        return showRequesterDetails;
    }

    public void setShowRequesterDetails(boolean showRequesterDetails) {
        this.showRequesterDetails = showRequesterDetails;
    }

    public Ticket getTicket() {
        return ticket;
    }

    public void setTicket(Ticket ticket) {
        this.ticket = ticket;
    }

    public WorkflowInstance getInstance() {
        return instance;
    }

    public void setInstance(WorkflowInstance instance) {
        this.instance = instance;
    }

    public List<WorkflowInstanceStage> getStages() {
        return stages;
    }

    public void setStages(List<WorkflowInstanceStage> stages) {
        this.stages = stages;
    }

    public List<TicketComment> getComments() {
        return comments;
    }

    public void setComments(List<TicketComment> comments) {
        this.comments = comments;
    }

    public List<TicketAttachment> getAttachments() {
        return attachments;
    }

    public void setAttachments(List<TicketAttachment> attachments) {
        this.attachments = attachments;
    }

    public TicketSla getSla() {
        return sla;
    }

    public void setSla(TicketSla sla) {
        this.sla = sla;
    }

    public WorkflowInstanceStage getCurrent() {
        return current;
    }

    public void setCurrent(WorkflowInstanceStage current) {
        this.current = current;
    }

    public List<WorkflowStageTransition> getAllowed() {
        return allowed;
    }

    public void setAllowed(List<WorkflowStageTransition> allowed) {
        this.allowed = allowed;
    }

    public boolean isCanAct() {
        return canAct;
    }

    public void setCanAct(boolean canAct) {
        this.canAct = canAct;
    }

    public boolean isCanCommentInternal() {
        return canCommentInternal;
    }

    public void setCanCommentInternal(boolean canCommentInternal) {
        this.canCommentInternal = canCommentInternal;
    }

    public boolean isShowInternalComments() {
        return showInternalComments;
    }

    public void setShowInternalComments(boolean showInternalComments) {
        this.showInternalComments = showInternalComments;
    }
}
