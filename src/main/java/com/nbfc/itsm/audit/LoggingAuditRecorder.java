package com.nbfc.itsm.audit;

import com.nbfc.itsm.domain.AuditLog;
import com.nbfc.itsm.domain.AuditLogRepository;
import com.nbfc.itsm.security.ItsmUserPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.servlet.http.HttpServletRequest;

@Service
public class LoggingAuditRecorder implements AuditRecorder {

    private static final Logger log = LoggerFactory.getLogger(LoggingAuditRecorder.class);

    private final AuditLogRepository auditLogRepository;

    public LoggingAuditRecorder(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Override
    public void record(String module, String action, String detail, String result) {
        record(module, action, detail, result, null);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String module, String action, String detail, String result, HttpServletRequest request) {
        log.info("audit module={} action={} result={}", module, action, result);
        AuditLog row = new AuditLog();
        row.setModuleCode(module);
        row.setActionCode(action);
        row.setNewValue(detail != null && detail.length() > 4000 ? detail.substring(0, 4000) + "…" : detail);
        row.setResultCode(result);
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof ItsmUserPrincipal) {
            ItsmUserPrincipal p = (ItsmUserPrincipal) auth.getPrincipal();
            row.setEmployeeId(p.getEmployeeId());
            row.setEmployeeNo(p.getEmployeeNo());
        }
        if (request != null) {
            row.setIpAddress(request.getRemoteAddr());
            String ua = request.getHeader("User-Agent");
            if (ua != null && ua.length() > 256) {
                ua = ua.substring(0, 256);
            }
            row.setUserAgent(ua);
        }
        auditLogRepository.save(row);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordTicket(String action, Long ticketId, String oldValue, String newValue) {
        log.info("audit module=TICKET action={} ticketId={}", action, ticketId);
        AuditLog row = new AuditLog();
        row.setModuleCode("TICKET");
        row.setActionCode(action);
        row.setTicketId(ticketId);
        row.setOldValue(oldValue);
        row.setNewValue(newValue);
        row.setResultCode("SUCCESS");
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof ItsmUserPrincipal) {
            ItsmUserPrincipal p = (ItsmUserPrincipal) auth.getPrincipal();
            row.setEmployeeId(p.getEmployeeId());
            row.setEmployeeNo(p.getEmployeeNo());
            if (!p.getRoleCodes().isEmpty()) {
                row.setRoleCode(p.getRoleCodes().get(0));
            }
        }
        auditLogRepository.save(row);
    }
}
