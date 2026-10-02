package com.nbfc.itsm.security;

import com.nbfc.itsm.config.ItsmProperties;
import com.nbfc.itsm.identity.LdapDirectoryClient;
import com.nbfc.itsm.identity.LdapPerson;
import com.nbfc.itsm.identity.PortalUserService;
import com.nbfc.itsm.validation.FieldLimits;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.naming.NamingException;

@Component
public class FailClosedLdapAuthenticationProvider implements AuthenticationProvider {

    private static final Logger log = LoggerFactory.getLogger(FailClosedLdapAuthenticationProvider.class);

    private final ItsmProperties properties;
    private final LdapDirectoryClient ldapDirectoryClient;
    private final PortalUserService portalUserService;

    private final LoginAttemptService loginAttempts;

    public FailClosedLdapAuthenticationProvider(ItsmProperties properties,
                                                LdapDirectoryClient ldapDirectoryClient,
                                                PortalUserService portalUserService) {
        this(properties, ldapDirectoryClient, portalUserService, new LoginAttemptService());
    }

    @org.springframework.beans.factory.annotation.Autowired
    public FailClosedLdapAuthenticationProvider(ItsmProperties properties,
                                                LdapDirectoryClient ldapDirectoryClient,
                                                PortalUserService portalUserService,
                                                LoginAttemptService loginAttempts) {
        this.properties = properties;
        this.ldapDirectoryClient = ldapDirectoryClient;
        this.portalUserService = portalUserService;
        this.loginAttempts = loginAttempts;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String username = authentication.getName();
        String password = authentication.getCredentials() == null ? "" : authentication.getCredentials().toString();
        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            throw new BadCredentialsException("Invalid credentials");
        }
        if (username.length() > FieldLimits.USERNAME_MAX || password.length() > FieldLimits.PASSWORD_MAX) {
            log.warn("Login rejected before LDAP: username or password longer than allowed");
            throw new BadCredentialsException("Invalid credentials");
        }
        if (!properties.getLdap().isConfigured()) {
            return null;
        }
        String ip = authentication.getDetails() instanceof org.springframework.security.web.authentication.WebAuthenticationDetails
                ? ((org.springframework.security.web.authentication.WebAuthenticationDetails) authentication.getDetails()).getRemoteAddress()
                : null;
        if (loginAttempts.isBlocked(username, ip)) {
            // Refused without contacting AD, so guessing cannot lock the account in AD either.
            log.warn("Login for user '{}' from {} refused: too many failed attempts", forLog(username), ip);
            throw new org.springframework.security.authentication.LockedException("too-many-attempts");
        }
        LdapPerson person;
        try {
            person = ldapDirectoryClient.authenticateAndLoad(username, password);
        } catch (NamingException ex) {
            loginAttempts.failed(username, ip);
            // Full stack trace in the server log; the login page still only says "Sign-in failed".
            log.warn("LDAP authentication failed for user '{}' (url={}): {}",
                    forLog(username), properties.getLdap().getUrl(), LdapDirectoryClient.diagnose(ex), ex);
            throw new BadCredentialsException("Invalid credentials");
        } catch (RuntimeException ex) {
            loginAttempts.failed(username, ip);
            log.warn("LDAP authentication failed for user '{}' (url={}) with an unexpected error: {}",
                    forLog(username), properties.getLdap().getUrl(), LdapDirectoryClient.diagnose(ex), ex);
            throw new BadCredentialsException("Invalid credentials");
        }
        loginAttempts.succeeded(username);
        try {
            ItsmUserPrincipal principal = portalUserService.loadActivePrincipal(person);
            return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        } catch (DisabledException ex) {
            log.warn("LDAP bind OK for user '{}' but portal access is disabled: {}", forLog(username), ex.getMessage());
            throw ex;
        } catch (RuntimeException ex) {
            log.error("LDAP bind OK for user '{}' but loading/creating the portal employee profile failed "
                    + "(database step, not LDAP): {}", forLog(username), ex.toString(), ex);
            throw new InternalAuthenticationServiceException("Portal profile could not be loaded", ex);
        }
    }

    /** Typed usernames go into the server log: no line breaks or control characters (no forged log lines). */
    static String forLog(String s) {
        if (s == null) {
            return "";
        }
        String clean = s.replaceAll("[\\p{Cntrl}]", "?");
        return clean.length() > 128 ? clean.substring(0, 128) + "…" : clean;
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
