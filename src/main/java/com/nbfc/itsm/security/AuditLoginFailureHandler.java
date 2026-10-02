package com.nbfc.itsm.security;

import com.nbfc.itsm.audit.AuditRecorder;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class AuditLoginFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    private final AuditRecorder auditRecorder;

    public AuditLoginFailureHandler(AuditRecorder auditRecorder) {
        this.auditRecorder = auditRecorder;
        setDefaultFailureUrl("/login?error");
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException, ServletException {
        String reason = exception.getClass().getSimpleName();
        String who = request.getParameter("username");
        if (who != null) {
            // Stored in the audit trail: no control characters, bounded length.
            who = who.replaceAll("[\\p{Cntrl}]", "?");
            who = who.length() > 128 ? who.substring(0, 128) : who;
        }
        String detail = (who == null ? "" : who) + " " + reason;
        auditRecorder.record("AUTH", "LOGIN", detail.trim(), "FAILURE", request);
        String url = "DisabledException".equals(reason) ? "/login?error=denied"
                : "LockedException".equals(reason) ? "/login?error=locked" : "/login?error";
        getRedirectStrategy().sendRedirect(request, response, url);
    }
}
