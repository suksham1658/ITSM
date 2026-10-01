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

/** One hand-over of a ticket: the service desk assigning it, or an implementor reassigning it. */
@Entity
@Table(name = "ticket_assignment_log")
public class TicketAssignmentLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ticket_assignment_log_id")
    private Long ticketAssignmentLogId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id", nullable = false)
    private Ticket ticket;

    /** The workflow step the hand-over happened on (service desk step or implementation step). */
    @Column(name = "workflow_instance_stage_id")
    private Long stageId;

    /** ASSIGN (service desk) or REASSIGN (implementor). */
    @Column(name = "action_code", nullable = false, length = 16)
    private String actionCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_employee_id")
    private Employee fromEmployee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_employee_id", nullable = false)
    private Employee toEmployee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "by_employee_id", nullable = false)
    private Employee byEmployee;

    @Column(name = "remarks", length = 2000)
    private String remarks;

    @Column(name = "created_at_utc", nullable = false)
    private Instant createdAtUtc = Instant.now();

    public Long getTicketAssignmentLogId() {
        return ticketAssignmentLogId;
    }

    public Ticket getTicket() {
        return ticket;
    }

    public void setTicket(Ticket ticket) {
        this.ticket = ticket;
    }

    public Long getStageId() {
        return stageId;
    }

    public void setStageId(Long stageId) {
        this.stageId = stageId;
    }

    public String getActionCode() {
        return actionCode;
    }

    public void setActionCode(String actionCode) {
        this.actionCode = actionCode;
    }

    public Employee getFromEmployee() {
        return fromEmployee;
    }

    public void setFromEmployee(Employee fromEmployee) {
        this.fromEmployee = fromEmployee;
    }

    public Employee getToEmployee() {
        return toEmployee;
    }

    public void setToEmployee(Employee toEmployee) {
        this.toEmployee = toEmployee;
    }

    public Employee getByEmployee() {
        return byEmployee;
    }

    public void setByEmployee(Employee byEmployee) {
        this.byEmployee = byEmployee;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }

    public Instant getCreatedAtUtc() {
        return createdAtUtc;
    }

    public void setCreatedAtUtc(Instant createdAtUtc) {
        this.createdAtUtc = createdAtUtc;
    }
}
