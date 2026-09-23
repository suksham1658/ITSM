package com.nbfc.itsm.security;

import com.nbfc.itsm.config.ItsmProperties;
import com.nbfc.itsm.identity.LdapDirectoryClient;
import com.nbfc.itsm.identity.PortalUserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import javax.naming.NamingException;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FailClosedLdapAuthenticationProviderTest {

    @Mock
    private LdapDirectoryClient ldapDirectoryClient;
    @Mock
    private PortalUserService portalUserService;

    @Test
    void unconfiguredLdapDoesNotAuthenticate() {
        ItsmProperties properties = new ItsmProperties();
        FailClosedLdapAuthenticationProvider provider =
                new FailClosedLdapAuthenticationProvider(properties, ldapDirectoryClient, portalUserService);
        Authentication result = provider.authenticate(
                new UsernamePasswordAuthenticationToken("jdoe", "Secret123!"));
        assertNull(result);
    }

    @Test
    void directoryFailureIsBadCredentialsNotOpen() throws Exception {
        ItsmProperties properties = new ItsmProperties();
        properties.getLdap().setUrl("ldap://127.0.0.1:389");
        when(ldapDirectoryClient.authenticateAndLoad(anyString(), anyString()))
                .thenThrow(new NamingException("directory down"));
        FailClosedLdapAuthenticationProvider provider =
                new FailClosedLdapAuthenticationProvider(properties, ldapDirectoryClient, portalUserService);
        assertThrows(BadCredentialsException.class, () ->
                provider.authenticate(new UsernamePasswordAuthenticationToken("jdoe", "Secret123!")));
    }

    @Test
    void fallbackAlwaysRejects() {
        FailClosedFallbackAuthenticationProvider fallback = new FailClosedFallbackAuthenticationProvider();
        assertThrows(BadCredentialsException.class, () ->
                fallback.authenticate(new UsernamePasswordAuthenticationToken("anyone", "x")));
    }
}
