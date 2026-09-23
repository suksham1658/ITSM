package com.nbfc.itsm.security;

import com.nbfc.itsm.audit.AuditRecorder;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class AuditLoginSuccessHandler implements AuthenticationSuccessHandler {

    private final AuditRecorder auditRecorder;
    private final SavedRequestAwareAuthenticationSuccessHandler delegate = new SavedRequestAwareAuthenticationSuccessHandler();

    public AuditLoginSuccessHandler(AuditRecorder auditRecorder) {
        this.auditRecorder = auditRecorder;
        this.delegate.setDefaultTargetUrl("/");
        this.delegate.setAlwaysUseDefaultTargetUrl(true);
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        String who = authentication.getName();
        auditRecorder.record("AUTH", "LOGIN", who, "SUCCESS", request);
        delegate.onAuthenticationSuccess(request, response, authentication);
    }
}
