package com.nbfc.itsm.security;

import com.nbfc.itsm.audit.AuditRecorder;
import com.nbfc.itsm.config.ItsmProperties;
import com.nbfc.itsm.identity.PortalUserService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.ExceptionTranslationFilter;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String CSP = "default-src 'self'; "
            + "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com https://cdnjs.cloudflare.com; "
            + "font-src 'self' https://fonts.gstatic.com https://cdnjs.cloudflare.com data:; "
            + "script-src 'self'; "
            + "img-src 'self' data:; "
            + "connect-src 'self'; "
            + "frame-ancestors 'none'";

    private final FailClosedLdapAuthenticationProvider ldapAuthenticationProvider;
    private final FailClosedFallbackAuthenticationProvider fallbackAuthenticationProvider;
    private final ObjectProvider<H2PreviewAuthenticationProvider> h2PreviewAuthenticationProvider;
    private final ItsmProperties properties;
    private final AuditRecorder auditRecorder;
    private final PortalUserService portalUserService;

    public SecurityConfig(FailClosedLdapAuthenticationProvider ldapAuthenticationProvider,
                          FailClosedFallbackAuthenticationProvider fallbackAuthenticationProvider,
                          ObjectProvider<H2PreviewAuthenticationProvider> h2PreviewAuthenticationProvider,
                          ItsmProperties properties,
                          AuditRecorder auditRecorder,
                          PortalUserService portalUserService) {
        this.ldapAuthenticationProvider = ldapAuthenticationProvider;
        this.fallbackAuthenticationProvider = fallbackAuthenticationProvider;
        this.h2PreviewAuthenticationProvider = h2PreviewAuthenticationProvider;
        this.properties = properties;
        this.auditRecorder = auditRecorder;
        this.portalUserService = portalUserService;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        AuditLoginSuccessHandler successHandler = new AuditLoginSuccessHandler(auditRecorder);
        AuditLoginFailureHandler failureHandler = new AuditLoginFailureHandler(auditRecorder);
        http
                .authenticationProvider(ldapAuthenticationProvider);
        H2PreviewAuthenticationProvider preview = h2PreviewAuthenticationProvider.getIfAvailable();
        if (preview != null) {
            http.authenticationProvider(preview);
        }
        http.authenticationProvider(fallbackAuthenticationProvider)
                .csrf()
                .and()
                .headers()
                    .contentSecurityPolicy(CSP)
                    .and()
                    .frameOptions().deny()
                    .referrerPolicy(ReferrerPolicyHeaderWriter.ReferrerPolicy.SAME_ORIGIN)
                    .and()
                    .httpStrictTransportSecurity().disable()
                .and()
                .authorizeRequests()
                    .antMatchers("/login", "/css/**", "/js/**", "/images/**", "/webfonts/**",
                            "/error", "/actuator/health", "/actuator/info").permitAll()
                    .antMatchers("/tickets/raise").hasAuthority("TICKET_CREATE")
                    .antMatchers("/tickets/team").hasAuthority("TICKET_VIEW_TEAM")
                    .antMatchers("/tickets/department").hasAuthority("TICKET_VIEW_DEPARTMENT")
                    .antMatchers("/tickets/security", "/risk").hasAuthority("TICKET_VIEW_SECURITY")
                    .antMatchers("/approvals").hasAuthority("TICKET_APPROVE_ASSIGNED_STAGE")
                    .antMatchers("/queue/desk", "/queue/assignment").hasAuthority("TICKET_VIEW_QUEUE_ALL")
                    .antMatchers("/queue/assign").hasAuthority("TICKET_ASSIGN")
                    .antMatchers("/queue/mine", "/queue/work", "/queue/implementation", "/tickets/changes")
                        .hasAuthority("TICKET_FULFIL")
                    .antMatchers("/sla", "/escalations").hasAuthority("SLA_MONITOR")
                    .antMatchers("/reports", "/reports/**").hasAuthority("REPORT_VIEW")
                    .antMatchers("/kb").hasAuthority("KB_READ")
                    .antMatchers("/audit").hasAuthority("AUDIT_VIEW")
                    .antMatchers("/assets").hasAuthority("ASSET_MANAGE")
                    .antMatchers("/admin/change-requests/**").hasAuthority("ADMIN_MASTERDATA_APPROVE")
                    .antMatchers("/admin/categories", "/admin/sla", "/admin/workflow", "/admin/config")
                        .hasAuthority("ADMIN_MASTERDATA_PROPOSE")
                    .antMatchers("/admin/roles").hasAuthority("ADMIN_USER_MANAGE")
                    .antMatchers("/admin/**").hasAuthority("ADMIN_USER_MANAGE")
                    .anyRequest().authenticated()
                .and()
                .exceptionHandling()
                    .accessDeniedPage("/403")
                .and()
                .formLogin()
                    .loginPage("/login")
                    .successHandler(successHandler)
                    .failureHandler(failureHandler)
                    .permitAll()
                .and()
                .logout()
                    .logoutUrl("/logout")
                    .logoutSuccessHandler((request, response, authentication) -> {
                        if (authentication != null) {
                            auditRecorder.record("AUTH", "LOGOUT", authentication.getName(), "SUCCESS", request);
                        }
                        response.sendRedirect(request.getContextPath() + "/login?logout");
                    })
                    .invalidateHttpSession(true)
                    .deleteCookies("JSESSIONID")
                    .permitAll()
                .and()
                .sessionManagement()
                    .sessionFixation().migrateSession()
                .and()
                .addFilterBefore(new AbsoluteSessionTimeoutFilter(properties), UsernamePasswordAuthenticationFilter.class)
                // Re-read roles/permissions each request so admin changes apply without a new login.
                .addFilterBefore(new PrincipalRefreshFilter(portalUserService), ExceptionTranslationFilter.class);
        return http.build();
    }
}
