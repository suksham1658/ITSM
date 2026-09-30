package com.nbfc.itsm.notification;

/** Published inside the ticket transaction; {@link TicketEmailService} mails the requester after commit. */
public class TicketEmailEvent {

    public enum Kind { CREATED, CLOSED }

    private final Long ticketId;
    private final Kind kind;

    public TicketEmailEvent(Long ticketId, Kind kind) {
        this.ticketId = ticketId;
        this.kind = kind;
    }

    public Long getTicketId() {
        return ticketId;
    }

    public Kind getKind() {
        return kind;
    }

    @Override
    public String toString() {
        return "TicketEmailEvent{" + kind + " ticket=" + ticketId + "}";
    }
}
