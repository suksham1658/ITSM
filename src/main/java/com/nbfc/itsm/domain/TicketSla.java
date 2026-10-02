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

    /** When the clock was paused (On Hold); null when running. */
    @Column(name = "paused_at_utc")
    private java.time.Instant pausedAtUtc;

    /** Business minutes spent on hold so far; added to the due times. */
    @Column(name = "paused_minutes", nullable = false)
    private int pausedMinutes;

    /** The first response came after the response due time. */
    @Column(name = "response_breached", nullable = false)
    private boolean responseBreached;

    @Column(name = "near_alerted_utc")
    private java.time.Instant nearAlertedUtc;

    @Column(name = "breach_alerted_utc")
    private java.time.Instant breachAlertedUtc;

    public java.time.Instant getPausedAtUtc() {
        return pausedAtUtc;
    }

    public void setPausedAtUtc(java.time.Instant pausedAtUtc) {
        this.pausedAtUtc = pausedAtUtc;
    }

    public int getPausedMinutes() {
        return pausedMinutes;
    }

    public void setPausedMinutes(int pausedMinutes) {
        this.pausedMinutes = pausedMinutes;
    }

    public boolean isResponseBreached() {
        return responseBreached;
    }

    public void setResponseBreached(boolean responseBreached) {
        this.responseBreached = responseBreached;
    }

    public java.time.Instant getNearAlertedUtc() {
        return nearAlertedUtc;
    }

    public void setNearAlertedUtc(java.time.Instant nearAlertedUtc) {
        this.nearAlertedUtc = nearAlertedUtc;
    }

    public java.time.Instant getBreachAlertedUtc() {
        return breachAlertedUtc;
    }

    public void setBreachAlertedUtc(java.time.Instant breachAlertedUtc) {
        this.breachAlertedUtc = breachAlertedUtc;
    }

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
