package com.nbfc.itsm.config;

import com.nbfc.itsm.admin.SystemSettingsService;
import org.springframework.boot.web.servlet.ServletListenerRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.servlet.http.HttpSessionEvent;
import javax.servlet.http.HttpSessionListener;

/**
 * Applies the idle timeout from System Configuration (session.idle-timeout-minutes) to every new session,
 * with the embedded server and with an external Tomcat alike.
 */
@Configuration
public class SessionTimeoutConfig {

    @Bean
    public ServletListenerRegistrationBean<HttpSessionListener> idleTimeoutListener(SystemSettingsService settings) {
        return new ServletListenerRegistrationBean<HttpSessionListener>(new HttpSessionListener() {
            @Override
            public void sessionCreated(HttpSessionEvent se) {
                se.getSession().setMaxInactiveInterval(settings.getIdleMinutes() * 60);
            }

            @Override
            public void sessionDestroyed(HttpSessionEvent se) {
                // nothing to clean up
            }
        });
    }
}
