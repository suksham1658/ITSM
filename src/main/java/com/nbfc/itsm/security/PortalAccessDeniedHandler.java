package com.nbfc.itsm.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Access denied. A missing / invalid CSRF token with no signed-in user means the session ended (a form left open
 * while the portal restarted or the session timed out): send the user to sign in again with a clear message.
 * Signed in with a wrong token, or a real permission refusal: the 403 page (any HTTP method).
 */
public class PortalAccessDeniedHandler implements AccessDeniedHandler {

    private static final Logger log = LoggerFactory.getLogger(PortalAccessDeniedHandler.class);

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex)
            throws IOException, ServletException {
        if (response.isCommitted()) {
            return;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean signedIn = auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken);
        if (ex instanceof CsrfException && !signedIn) {
            // No session any more (portal restarted / session timed out) and a form from before was sent.
            log.warn("{} {} refused: form security token from an ended session (e.g. after a restart) - "
                    + "user sent to sign in again", request.getMethod(), request.getRequestURI());
            response.sendRedirect(request.getContextPath() + "/login?ended=1");
            return;
        }
        // Signed in with a wrong token (possible forged request) or a real permission refusal: 403 page.
        log.warn("{} {} refused: {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        request.getRequestDispatcher("/403").forward(request, response);
    }
}
