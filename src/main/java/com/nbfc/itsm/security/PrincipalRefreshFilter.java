package com.nbfc.itsm.security;

import com.nbfc.itsm.identity.PortalUserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;

/**
 * Keeps a signed-in user's roles and permissions in step with the database. Without this, the
 * authorities are frozen at login, so a new user who is given the Employee role (or anyone whose
 * roles change) sees no difference until they sign in again. When the employee is disabled or
 * removed, the session is ended.
 */
public class PrincipalRefreshFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(PrincipalRefreshFilter.class);

    private final PortalUserService portalUserService;

    public PrincipalRefreshFilter(PortalUserService portalUserService) {
        this.portalUserService = portalUserService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return path.startsWith("/css/") || path.startsWith("/js/") || path.startsWith("/images/")
                || path.startsWith("/webfonts/") || path.startsWith("/actuator/") || path.equals("/favicon.ico");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof ItsmUserPrincipal) {
            ItsmUserPrincipal current = (ItsmUserPrincipal) auth.getPrincipal();
            ItsmUserPrincipal fresh = portalUserService.refresh(current);
            if (fresh == null) {
                log.info("Ending session for employee {}: portal access disabled or profile removed",
                        current.getEmployeeNo());
                SecurityContextHolder.clearContext();
                HttpSession session = request.getSession(false);
                if (session != null) {
                    session.invalidate();
                }
                response.sendRedirect(request.getContextPath() + "/login?error=denied");
                return;
            }
            if (!fresh.fingerprint().equals(current.fingerprint())) {
                UsernamePasswordAuthenticationToken updated =
                        new UsernamePasswordAuthenticationToken(fresh, null, fresh.getAuthorities());
                updated.setDetails(auth.getDetails());
                SecurityContext context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(updated);
                // Stored back into the session by SecurityContextPersistenceFilter.
                SecurityContextHolder.setContext(context);
            }
        }
        chain.doFilter(request, response);
    }
}
