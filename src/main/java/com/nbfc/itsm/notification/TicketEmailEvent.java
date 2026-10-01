package com.nbfc.itsm.notification;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Published inside the ticket transaction; {@link TicketEmailService} sends the e-mails after commit. */
public class TicketEmailEvent {

    /**
     * CREATED / CLOSED / AUTO_CLOSED (closed for lack of confirmation, with a re-open link): to the requester. WAITING: the ticket is now in the queue of
     * {@link #getRecipientIds()} (the people who must act on step {@link #getStageId()}).
     */
    public enum Kind { CREATED, CLOSED, WAITING, AUTO_CLOSED }

    private final Long ticketId;
    private final Kind kind;
    private final Long stageId;
    private final List<Long> recipientIds;

    public TicketEmailEvent(Long ticketId, Kind kind) {
        this(ticketId, kind, null, Collections.<Long>emptyList());
    }

    public TicketEmailEvent(Long ticketId, Kind kind, Long stageId, List<Long> recipientIds) {
        this.ticketId = ticketId;
        this.kind = kind;
        this.stageId = stageId;
        this.recipientIds = Collections.unmodifiableList(new ArrayList<Long>(recipientIds));
    }

    public Long getTicketId() {
        return ticketId;
    }

    public Kind getKind() {
        return kind;
    }

    public Long getStageId() {
        return stageId;
    }

    public List<Long> getRecipientIds() {
        return recipientIds;
    }

    @Override
    public String toString() {
        return "TicketEmailEvent{" + kind + " ticket=" + ticketId
                + (kind == Kind.WAITING ? " stage=" + stageId + " to=" + recipientIds : "") + "}";
    }
}
