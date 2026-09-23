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
@Table(name = "ticket_sla")
public class TicketSla {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ticket_sla_id")
    private Long ticketSlaId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id", nullable = false)
    private Ticket ticket;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sla_policy_id", nullable = false)
    private SlaPolicy slaPolicy;

    @Column(name = "sla_start_utc", nullable = false)
    private Instant slaStartUtc;

    @Column(name = "response_due_utc", nullable = false)
    private Instant responseDueUtc;

    @Column(name = "resolve_due_utc", nullable = false)
    private Instant resolveDueUtc;

    @Column(name = "first_response_utc")
    private Instant firstResponseUtc;

    @Column(name = "resolved_utc")
    private Instant resolvedUtc;

    @Column(name = "paused", nullable = false)
    private boolean paused;

    @Column(name = "state_code", nullable = false, length = 16)
    private String stateCode;

    public Long getTicketSlaId() {
        return ticketSlaId;
    }

    public void setTicketSlaId(Long ticketSlaId) {
        this.ticketSlaId = ticketSlaId;
    }

    public Ticket getTicket() {
        return ticket;
    }

    public void setTicket(Ticket ticket) {
        this.ticket = ticket;
    }

    public SlaPolicy getSlaPolicy() {
        return slaPolicy;
    }

    public void setSlaPolicy(SlaPolicy slaPolicy) {
        this.slaPolicy = slaPolicy;
    }

    public Instant getSlaStartUtc() {
        return slaStartUtc;
    }

    public void setSlaStartUtc(Instant slaStartUtc) {
        this.slaStartUtc = slaStartUtc;
    }

    public Instant getResponseDueUtc() {
        return responseDueUtc;
    }

    public void setResponseDueUtc(Instant responseDueUtc) {
        this.responseDueUtc = responseDueUtc;
    }

    public Instant getResolveDueUtc() {
        return resolveDueUtc;
    }

    public void setResolveDueUtc(Instant resolveDueUtc) {
        this.resolveDueUtc = resolveDueUtc;
    }

    public Instant getFirstResponseUtc() {
        return firstResponseUtc;
    }

    public void setFirstResponseUtc(Instant firstResponseUtc) {
        this.firstResponseUtc = firstResponseUtc;
    }

    public Instant getResolvedUtc() {
        return resolvedUtc;
    }

    public void setResolvedUtc(Instant resolvedUtc) {
        this.resolvedUtc = resolvedUtc;
    }

    public boolean isPaused() {
        return paused;
    }

    public void setPaused(boolean paused) {
        this.paused = paused;
    }

    public String getStateCode() {
        return stateCode;
    }

    public void setStateCode(String stateCode) {
        this.stateCode = stateCode;
    }
}
