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
