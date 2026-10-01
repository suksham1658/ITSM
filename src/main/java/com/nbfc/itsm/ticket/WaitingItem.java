package com.nbfc.itsm.ticket;

import com.nbfc.itsm.domain.Ticket;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** One row on the Approvals page: a ticket and the step that is waiting for the signed-in user. */
public class WaitingItem {

    /** What the user has to do at each kind of step; other step types never wait for a person. */
    static final Map<String, String> ACTION_BY_STAGE_TYPE;

    static {
        Map<String, String> m = new HashMap<String, String>();
        m.put("APPROVAL", "Approve");
        m.put("ASSIGNMENT", "Assign to implementor");
        m.put("FULFILMENT", "Work on it");
        m.put("CONFIRMATION", "Confirm resolution");
        ACTION_BY_STAGE_TYPE = Collections.unmodifiableMap(m);
    }

    private final Ticket ticket;
    private final String stageType;
    private final String stepLabel;

    WaitingItem(Ticket ticket, String stageType, String stepLabel) {
        this.ticket = ticket;
        this.stageType = stageType;
        this.stepLabel = stepLabel;
    }

    public Ticket getTicket() {
        return ticket;
    }

    public String getStageType() {
        return stageType;
    }

    public String getStepLabel() {
        return stepLabel;
    }

    public String getAction() {
        return ACTION_BY_STAGE_TYPE.get(stageType);
    }
}
