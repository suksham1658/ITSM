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

    public FailClosedLdapAuthenticationProvider(ItsmProperties properties,
                                                LdapDirectoryClient ldapDirectoryClient,
                                                PortalUserService portalUserService) {
        this.properties = properties;
        this.ldapDirectoryClient = ldapDirectoryClient;
        this.portalUserService = portalUserService;
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
        LdapPerson person;
        try {
            person = ldapDirectoryClient.authenticateAndLoad(username, password);
        } catch (NamingException ex) {
            // Full stack trace in the server log; the login page still only says "Sign-in failed".
            log.warn("LDAP authentication failed for user '{}' (url={}): {}",
                    username, properties.getLdap().getUrl(), LdapDirectoryClient.diagnose(ex), ex);
            throw new BadCredentialsException("Invalid credentials");
        } catch (RuntimeException ex) {
            log.warn("LDAP authentication failed for user '{}' (url={}) with an unexpected error: {}",
                    username, properties.getLdap().getUrl(), LdapDirectoryClient.diagnose(ex), ex);
            throw new BadCredentialsException("Invalid credentials");
        }
        try {
            ItsmUserPrincipal principal = portalUserService.loadActivePrincipal(person);
            return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        } catch (DisabledException ex) {
            log.warn("LDAP bind OK for user '{}' but portal access is disabled: {}", username, ex.getMessage());
            throw ex;
        } catch (RuntimeException ex) {
            log.error("LDAP bind OK for user '{}' but loading/creating the portal employee profile failed "
                    + "(database step, not LDAP): {}", username, ex.toString(), ex);
            throw new InternalAuthenticationServiceException("Portal profile could not be loaded", ex);
        }
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
