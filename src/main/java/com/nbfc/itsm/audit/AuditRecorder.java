package com.nbfc.itsm.audit;

import javax.servlet.http.HttpServletRequest;

public interface AuditRecorder {

    void record(String module, String action, String detail, String result);

    void record(String module, String action, String detail, String result, HttpServletRequest request);

    void recordTicket(String action, Long ticketId, String oldValue, String newValue);
}
