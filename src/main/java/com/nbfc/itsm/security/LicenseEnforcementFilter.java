package com.nbfc.itsm.security;

import com.nbfc.itsm.license.LicenseService;
import com.nbfc.itsm.license.LicenseState;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Keeps the licensing quiet while the term is valid or in grace. Once the grace period is over (or the license
 * is missing/invalid/tampered), everyone except a System Administrator is sent to a "temporarily unavailable"
 * page, so the administrator can still sign in and install a renewal and no one else is locked out without a
 * reason. The login, logout, the unavailable page and static files always stay reachable.
 */
public class LicenseEnforcementFilter extends OncePerRequestFilter {

    private final LicenseService licenseService;
    private final boolean enforce;

    public LicenseEnforcementFilter(LicenseService licenseService, boolean enforce) {
        this.licenseService = licenseService;
        this.enforce = enforce;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return path.startsWith("/css/") || path.startsWith("/js/") || path.startsWith("/images/")
                || path.startsWith("/webfonts/") || path.startsWith("/actuator/")
                || path.equals("/favicon.ico") || path.equals("/login") || path.equals("/logout")
                || path.equals("/license-unavailable");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!enforce) {
            chain.doFilter(request, response);
            return;
        }
        LicenseState state = licenseService.current();
        if (state.isOperational() || isSystemAdministrator()) {
            chain.doFilter(request, response);
            return;
        }
        // Past grace and not a System Administrator: hold the portal.
        response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        response.sendRedirect(request.getContextPath() + "/license-unavailable");
    }

    private boolean isSystemAdministrator() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof ItsmUserPrincipal) {
            return ((ItsmUserPrincipal) auth.getPrincipal()).getRoleCodes().contains("SYSTEM_ADMINISTRATOR");
        }
        return false;
    }
}
