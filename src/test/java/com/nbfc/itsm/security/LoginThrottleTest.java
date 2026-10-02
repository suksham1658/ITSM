package com.nbfc.itsm.security;

import com.nbfc.itsm.config.ItsmProperties;
import com.nbfc.itsm.identity.LdapDirectoryClient;
import com.nbfc.itsm.identity.PortalUserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import javax.naming.NamingException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Too many wrong passwords: refused for a while, without asking AD (so AD cannot be locked through the portal). */
@ExtendWith(MockitoExtension.class)
class LoginThrottleTest {

    @Mock private LdapDirectoryClient ldapDirectoryClient;
    @Mock private PortalUserService portalUserService;

    @Test
    void fiveWrongPasswordsBlockTheUsernameButNotOthers() {
        LoginAttemptService s = new LoginAttemptService();
        for (int i = 0; i < LoginAttemptService.MAX_PER_USER - 1; i++) {
            s.failed("CORP\\Asha", "10.0.0.5");
        }
        assertFalse(s.isBlocked("asha", "10.0.0.9"), "4 failures: still allowed");
        s.failed("asha@corp.in", "10.0.0.5");
        assertTrue(s.isBlocked("ASHA", "10.0.0.9"), "same account however it is typed");
        assertFalse(s.isBlocked("ravi", "10.0.0.9"), "other users unaffected");
    }

    @Test
    void oneComputerTryingManyNamesIsBlocked() {
        LoginAttemptService s = new LoginAttemptService();
        for (int i = 0; i < LoginAttemptService.MAX_PER_IP; i++) {
            s.failed("user" + i, "10.9.9.9");
        }
        assertTrue(s.isBlocked("someone-new", "10.9.9.9"));
        assertFalse(s.isBlocked("someone-new", "10.9.9.10"));
    }

    @Test
    void correctPasswordClearsTheCount() {
        LoginAttemptService s = new LoginAttemptService();
        for (int i = 0; i < LoginAttemptService.MAX_PER_USER - 1; i++) {
            s.failed("asha", "10.0.0.5");
        }
        s.succeeded("asha");
        s.failed("asha", "10.0.0.6");
        assertFalse(s.isBlocked("asha", "10.0.0.7"));
    }

    @Test
    void blockedLoginsNeverReachTheDirectory() throws Exception {
        ItsmProperties properties = new ItsmProperties();
        properties.getLdap().setUrl("ldap://127.0.0.1:389");
        when(ldapDirectoryClient.authenticateAndLoad(anyString(), anyString())).thenThrow(new NamingException("bad password"));
        FailClosedLdapAuthenticationProvider provider =
                new FailClosedLdapAuthenticationProvider(properties, ldapDirectoryClient, portalUserService, new LoginAttemptService());
        for (int i = 0; i < LoginAttemptService.MAX_PER_USER; i++) {
            assertThrows(BadCredentialsException.class,
                    () -> provider.authenticate(new UsernamePasswordAuthenticationToken("asha", "Wrong123!")));
        }
        assertThrows(LockedException.class,
                () -> provider.authenticate(new UsernamePasswordAuthenticationToken("asha", "Right123!")));
        verify(ldapDirectoryClient, times(LoginAttemptService.MAX_PER_USER)).authenticateAndLoad(anyString(), anyString());
    }

    @Test
    void typedUsernamesCannotForgeLogLines() {
        assertEquals("evil?? audit module=AUTH result=SUCCESS",
                FailClosedLdapAuthenticationProvider.forLog("evil\r\n audit module=AUTH result=SUCCESS"));
    }
}
