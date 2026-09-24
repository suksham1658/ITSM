package com.nbfc.itsm.identity;

import com.nbfc.itsm.config.ItsmProperties;
import org.junit.jupiter.api.Test;

import javax.naming.AuthenticationException;
import javax.naming.CommunicationException;
import javax.naming.NamingException;
import java.net.ConnectException;
import java.net.ServerSocket;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LdapDiagnosisTest {

    @Test
    void activeDirectorySubCodesAreExplained() {
        assertTrue(LdapDirectoryClient.diagnose(ad("52e")).contains("wrong username or password"));
        assertTrue(LdapDirectoryClient.diagnose(ad("525")).contains("not found"));
        assertTrue(LdapDirectoryClient.diagnose(ad("775")).contains("locked"));
        assertTrue(LdapDirectoryClient.diagnose(ad("533")).contains("disabled"));
        assertTrue(LdapDirectoryClient.diagnose(ad("532")).contains("expired"));
        assertTrue(LdapDirectoryClient.diagnose(new AuthenticationException("[LDAP: error code 49 - Invalid Credentials]"))
                .startsWith("BIND REJECTED"));
    }

    @Test
    void connectionProblemsAreReportedAsCannotConnect() {
        CommunicationException ex = new CommunicationException("dc.example:389");
        ex.setRootCause(new ConnectException("Connection refused"));
        assertTrue(LdapDirectoryClient.diagnose(ex).startsWith("CANNOT CONNECT"));
    }

    @Test
    void unreachableServerIsDiagnosedEndToEnd() throws Exception {
        int closedPort;
        try (ServerSocket s = new ServerSocket(0)) {
            closedPort = s.getLocalPort();
        }
        ItsmProperties props = new ItsmProperties();
        props.getLdap().setUrl("ldap://127.0.0.1:" + closedPort);
        props.getLdap().setBaseDn("dc=example,dc=in");
        props.getLdap().setUserDnPattern("uid={0},dc=example,dc=in");
        props.getLdap().setConnectTimeoutMs(1000);
        LdapDirectoryClient client = new LdapDirectoryClient(props);

        NamingException ex = assertThrows(NamingException.class, () -> client.authenticateAndLoad("jdoe", "secret"));
        assertTrue(LdapDirectoryClient.diagnose(ex).startsWith("CANNOT CONNECT"), LdapDirectoryClient.diagnose(ex));
    }

    private static AuthenticationException ad(String code) {
        return new AuthenticationException("[LDAP: error code 49 - 80090308: LdapErr: DSID-0C09044E, comment: "
                + "AcceptSecurityContext error, data " + code + ", v2580]");
    }
}
