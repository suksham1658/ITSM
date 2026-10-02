package com.nbfc.itsm.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.servlet.SessionCookieConfig;
import javax.servlet.SessionTrackingMode;
import java.util.EnumSet;

/**
 * Session cookie rules that also apply when the WAR runs on the server's own Tomcat (the
 * {@code server.servlet.session.*} properties only reach the embedded Tomcat):
 * <ul>
 *   <li>session id only in a cookie, never in the URL (a ";jsessionid=" link could leak the session);</li>
 *   <li>HttpOnly, so page scripts cannot read the session cookie;</li>
 *   <li>Secure when SESSION_COOKIE_SECURE=true (set it once the portal runs on HTTPS).</li>
 * </ul>
 */
@Configuration
public class SessionCookieHardening {

    @Bean
    public ServletContextInitializer sessionCookieInitializer(
            @Value("${server.servlet.session.cookie.secure:false}") boolean secure) {
        return servletContext -> {
            servletContext.setSessionTrackingModes(EnumSet.of(SessionTrackingMode.COOKIE));
            SessionCookieConfig cookie = servletContext.getSessionCookieConfig();
            cookie.setHttpOnly(true);
            cookie.setSecure(secure);
        };
    }
}
