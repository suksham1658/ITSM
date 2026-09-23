package com.nbfc.itsm.security;

import com.nbfc.itsm.config.ItsmProperties;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.time.Duration;

public class AbsoluteSessionTimeoutFilter extends OncePerRequestFilter {

    private final ItsmProperties properties;

    public AbsoluteSessionTimeoutFilter(ItsmProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        Duration abs = properties.getSecurity().getSessionAbsoluteTimeout();
        if (session != null && abs != null && abs.toMillis() > 0) {
            long age = System.currentTimeMillis() - session.getCreationTime();
            if (age > abs.toMillis()) {
                session.invalidate();
                if (!request.getRequestURI().contains("/login")) {
                    response.sendRedirect(request.getContextPath() + "/login?expired=1");
                    return;
                }
            }
        }
        filterChain.doFilter(request, response);
    }
}
