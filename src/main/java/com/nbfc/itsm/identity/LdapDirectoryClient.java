package com.nbfc.itsm.identity;

import com.nbfc.itsm.config.ItsmProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.LdapName;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * LDAP bind + attribute search. Never logs passwords. Fail-closed on any directory error.
 */
@Component
public class LdapDirectoryClient {

    private static final Logger log = LoggerFactory.getLogger(LdapDirectoryClient.class);

    private final ItsmProperties properties;

    public LdapDirectoryClient(ItsmProperties properties) {
        this.properties = properties;
    }

    public LdapPerson authenticateAndLoad(String username, String password) throws NamingException {
        ItsmProperties.Ldap ldap = properties.getLdap();
        if (!ldap.isConfigured()) {
            throw new NamingException("LDAP is not configured");
        }
        String userDn = resolveUserDn(username, password, ldap);
        DirContext userCtx = bind(userDn, password, ldap);
        try {
            LdapPerson person = searchPerson(userCtx, username, ldap, userDn);
            person.setManagerChain(loadManagerChain(userCtx, person, ldap));
            return person;
        } finally {
            closeQuietly(userCtx);
        }
    }

    /** Longest management chain followed upwards from a user (guards against loops and very deep trees). */
    static final int MAX_MANAGER_DEPTH = 10;

    /**
     * Follows the {@code manager} attribute (a DN) upwards and returns the managers, immediate
     * manager first. Reading the chain is best effort: a directory error stops the walk and is
     * logged, but never blocks the login.
     */
    List<LdapPerson> loadManagerChain(DirContext ctx, LdapPerson person, ItsmProperties.Ldap ldap) {
        List<LdapPerson> chain = new ArrayList<LdapPerson>();
        Set<String> seen = new HashSet<String>();
        if (person.getDn() != null) {
            seen.add(person.getDn().toLowerCase(Locale.ROOT));
        }
        String next = person.getManagerDn();
        while (StringUtils.hasText(next) && chain.size() < MAX_MANAGER_DEPTH
                && seen.add(next.toLowerCase(Locale.ROOT))) {
            try {
                // LdapName, not a String: AD names often contain '/', which JNDI would treat as a composite name.
                Attributes attrs = ctx.getAttributes(new LdapName(next), personAttributes(ldap));
                LdapPerson manager = new LdapPerson();
                manager.setDn(next);
                mapAttributes(manager, attrs, ldap);
                if (!StringUtils.hasText(manager.getSamAccountName())) {
                    log.warn("LDAP manager entry {} has no sAMAccountName/uid; stopping the manager chain here", next);
                    break;
                }
                chain.add(manager);
                next = manager.getManagerDn();
            } catch (NamingException ex) {
                log.warn("Could not read LDAP manager entry {}: {}", next, diagnose(ex));
                break;
            }
        }
        return chain;
    }

    private static String[] personAttributes(ItsmProperties.Ldap ldap) {
        return new String[] {
                "sAMAccountName", "uid", ldap.getEmployeeIdAttribute(), "employeeNumber", "displayName", "cn", "mail",
                "title", "userPrincipalName", "givenName", "sn", ldap.getManagerAttribute(), "department"
        };
    }

    private String resolveUserDn(String username, String password, ItsmProperties.Ldap ldap) throws NamingException {
        if (username.indexOf('=') >= 0 && username.indexOf(',') >= 0) {
            bind(STAGE_USER_BIND, username, password, ldap).close();
            return username;
        }
        if (StringUtils.hasText(ldap.getUserDnPattern())) {
            String dn = ldap.getUserDnPattern().replace("{0}", sanitize(username));
            bind(STAGE_USER_BIND, dn, password, ldap).close();
            return dn;
        }
        if (StringUtils.hasText(ldap.getBindDn()) && StringUtils.hasText(ldap.getBindPassword())) {
            DirContext service = bind(STAGE_SERVICE_BIND, ldap.getBindDn(), ldap.getBindPassword(), ldap);
            try {
                String found;
                try {
                    found = searchDn(service, username, ldap);
                } catch (NamingException ex) {
                    logStageFailure(STAGE_USER_SEARCH, ldap.getBaseDn(), ldap, ex);
                    throw ex;
                }
                if (!StringUtils.hasText(found)) {
                    NamingException ex = new NamingException("User not found");
                    logStageFailure(STAGE_USER_SEARCH, ldap.getBaseDn(), ldap, ex);
                    throw ex;
                }
                DirContext check = bind(STAGE_USER_BIND, found, password, ldap);
                closeQuietly(check);
                return found;
            } finally {
                closeQuietly(service);
            }
        }
        if (StringUtils.hasText(ldap.getBaseDn())) {
            String dn = "CN=" + sanitize(username) + "," + ldap.getBaseDn();
            bind(STAGE_USER_BIND, dn, password, ldap).close();
            return dn;
        }
        throw new NamingException("Cannot resolve user DN");
    }

    private DirContext bind(String principal, String password, ItsmProperties.Ldap ldap) throws NamingException {
        return bind(STAGE_USER_BIND, principal, password, ldap);
    }

    private DirContext bind(String stage, String principal, String password, ItsmProperties.Ldap ldap)
            throws NamingException {
        Hashtable<String, String> env = new Hashtable<String, String>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        env.put(Context.PROVIDER_URL, ldap.getUrl());
        env.put(Context.SECURITY_AUTHENTICATION, "simple");
        env.put(Context.SECURITY_PRINCIPAL, principal);
        env.put(Context.SECURITY_CREDENTIALS, password);
        env.put("com.sun.jndi.ldap.connect.timeout", String.valueOf(ldap.getConnectTimeoutMs()));
        env.put("com.sun.jndi.ldap.read.timeout", String.valueOf(ldap.getReadTimeoutMs()));
        try {
            return new InitialDirContext(env);
        } catch (NamingException ex) {
            logStageFailure(stage, principal, ldap, ex);
            throw ex;
        }
    }

    // ------------------------------------------------------------------ diagnostics

    static final String STAGE_SERVICE_BIND = "service-account bind (LDAP_BIND_DN)";
    static final String STAGE_USER_SEARCH = "user search (LDAP_BASE_DN + LDAP_USER_SEARCH_FILTER)";
    static final String STAGE_USER_BIND = "user bind (the password typed on the login page)";

    /** One line naming the failing step and the likely cause. The password is never logged. */
    private static void logStageFailure(String stage, String principal, ItsmProperties.Ldap ldap, NamingException ex) {
        log.warn("LDAP step failed: {} | url={} | principal/base={} | cause: {}",
                stage, ldap.getUrl(), principal, diagnose(ex));
    }

    /**
     * Human-readable cause for an LDAP failure: unreachable server, bad credentials (with the
     * Active Directory sub-code such as {@code data 52e}), locked/disabled/expired account, etc.
     */
    public static String diagnose(Throwable ex) {
        Throwable root = rootCause(ex);
        if (ex instanceof javax.naming.CommunicationException || ex instanceof javax.naming.ServiceUnavailableException
                || root instanceof java.net.ConnectException || root instanceof java.net.UnknownHostException
                || root instanceof java.net.SocketTimeoutException || root instanceof java.net.NoRouteToHostException
                || root instanceof javax.net.ssl.SSLException) {
            return "CANNOT CONNECT to the LDAP server (" + root.getClass().getSimpleName() + ": " + root.getMessage()
                    + "). Check LDAP_URL host/port, DNS, firewall, and ldaps:// certificate trust.";
        }
        String msg = String.valueOf(ex.getMessage());
        if (ex instanceof javax.naming.AuthenticationException) {
            String code = adSubCode(msg);
            if ("525".equals(code)) {
                return "BIND REJECTED: user/principal not found in AD (AD code 525).";
            }
            if ("52e".equals(code)) {
                return "BIND REJECTED: wrong username or password (AD code 52e).";
            }
            if ("530".equals(code) || "531".equals(code)) {
                return "BIND REJECTED: logon not permitted at this time/workstation (AD code " + code + ").";
            }
            if ("532".equals(code)) {
                return "BIND REJECTED: password expired (AD code 532).";
            }
            if ("533".equals(code)) {
                return "BIND REJECTED: account disabled (AD code 533).";
            }
            if ("701".equals(code)) {
                return "BIND REJECTED: account expired (AD code 701).";
            }
            if ("773".equals(code)) {
                return "BIND REJECTED: user must reset password (AD code 773).";
            }
            if ("775".equals(code)) {
                return "BIND REJECTED: account locked out (AD code 775).";
            }
            return "BIND REJECTED: invalid credentials (" + msg + ").";
        }
        if (ex instanceof javax.naming.NameNotFoundException) {
            return "ENTRY NOT FOUND: the base DN or user DN does not exist (" + msg + "). Check LDAP_BASE_DN.";
        }
        if (ex instanceof javax.naming.InvalidNameException) {
            return "INVALID DN syntax (" + msg + "). Check LDAP_BIND_DN / LDAP_USER_DN_PATTERN.";
        }
        if (ex instanceof javax.naming.NoPermissionException) {
            return "NO PERMISSION: the bound account may not search here (" + msg + ").";
        }
        if ("User not found".equals(msg)) {
            return "USER NOT FOUND: the search filter matched no entry under the base DN. Check the username, "
                    + "LDAP_BASE_DN and LDAP_USER_SEARCH_FILTER.";
        }
        return ex.getClass().getSimpleName() + ": " + msg;
    }

    private static String adSubCode(String message) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("data ([0-9a-fA-F]{3})").matcher(message);
        return m.find() ? m.group(1).toLowerCase(java.util.Locale.ROOT) : null;
    }

    private static Throwable rootCause(Throwable ex) {
        Throwable t = ex;
        for (int i = 0; i < 10; i++) {
            Throwable next = t instanceof NamingException && ((NamingException) t).getRootCause() != null
                    ? ((NamingException) t).getRootCause()
                    : t.getCause();
            if (next == null || next == t) {
                return t;
            }
            t = next;
        }
        return t;
    }

    private String searchDn(DirContext ctx, String username, ItsmProperties.Ldap ldap) throws NamingException {
        SearchControls sc = new SearchControls();
        sc.setSearchScope(SearchControls.SUBTREE_SCOPE);
        sc.setCountLimit(1);
        String filter = ldap.getUserSearchFilter().replace("{0}", escapeFilter(sanitize(username)));
        String base = StringUtils.hasText(ldap.getBaseDn()) ? ldap.getBaseDn() : "";
        NamingEnumeration<SearchResult> results = ctx.search(base, filter, sc);
        try {
            if (results.hasMore()) {
                return results.next().getNameInNamespace();
            }
            return null;
        } finally {
            results.close();
        }
    }

    private LdapPerson searchPerson(DirContext ctx, String username, ItsmProperties.Ldap ldap, String userDn)
            throws NamingException {
        SearchControls sc = new SearchControls();
        sc.setSearchScope(SearchControls.SUBTREE_SCOPE);
        sc.setCountLimit(1);
        sc.setReturningAttributes(personAttributes(ldap));
        String filter = ldap.getUserSearchFilter().replace("{0}", escapeFilter(sanitize(username)));
        String base = StringUtils.hasText(ldap.getBaseDn()) ? ldap.getBaseDn() : "";
        NamingEnumeration<SearchResult> results;
        try {
            results = ctx.search(base, filter, sc);
        } catch (NamingException ex) {
            log.debug("User search under base failed; using bind DN attributes only");
            results = null;
        }
        LdapPerson person = new LdapPerson();
        person.setDn(userDn);
        if (results != null && results.hasMore()) 
        { 
        	   
            SearchResult sr = results.next();
            
         // DEBUG: print attributes received from Active Directory
            System.out.println("========== LDAP ATTRIBUTES ==========");

            Attributes attrs = sr.getAttributes();

            NamingEnumeration<? extends Attribute> allAttributes =
                    attrs.getAll();

            while (allAttributes.hasMore()) {

                Attribute attribute = allAttributes.next();

                System.out.println(
                        attribute.getID() + " = " + attribute.get()
                );
            }

            System.out.println("=====================================");
            
            person.setDn(sr.getNameInNamespace());
            mapAttributes(person, sr.getAttributes(), ldap);
            results.close();
        } else {
            person.setSamAccountName(sanitize(username));
        }
        if (!StringUtils.hasText(person.getSamAccountName())) {
            person.setSamAccountName(sanitize(username));
        }
        return person;
    }

    private void mapAttributes(LdapPerson person, Attributes attrs, ItsmProperties.Ldap ldap) throws NamingException {
        person.setSamAccountName(first(attrs, "sAMAccountName", "uid"));

        person.setEmployeeNo(first(
                attrs,
                ldap.getEmployeeIdAttribute(),
                "employeeNumber",
                "sAMAccountName"
        ));
        person.setDisplayName(first(attrs, "displayName", "cn"));
        person.setEmail(first(attrs, "mail"));
        person.setDesignation(first(attrs, "title"));
        person.setUpn(first(attrs, "userPrincipalName", "mail"));
        person.setManagerDn(first(attrs, ldap.getManagerAttribute()));
        person.setDepartment(first(attrs, "department"));
    }

    private String first(Attributes attrs, String... ids) throws NamingException {
        if (attrs == null) {
            return null;
        }
        for (int i = 0; i < ids.length; i++) {
            Attribute a = attrs.get(ids[i]);
            if (a != null && a.get() != null) {
                return a.get().toString();
            }
        }
        return null;
    }

    private static String sanitize(String username) {
        int slash = username.lastIndexOf('\\');
        if (slash >= 0) {
            return username.substring(slash + 1);
        }
        return username;
    }

    private static String escapeFilter(String value) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\':
                    sb.append("\\5c");
                    break;
                case '*':
                    sb.append("\\2a");
                    break;
                case '(':
                    sb.append("\\28");
                    break;
                case ')':
                    sb.append("\\29");
                    break;
                case '\0':
                    sb.append("\\00");
                    break;
                default:
                    sb.append(c);
            }
        }
        return sb.toString();
    }

    private static void closeQuietly(DirContext ctx) {
        if (ctx == null) {
            return;
        }
        try {
            ctx.close();
        } catch (NamingException ignored) {
            // ignore
        }
    }
}
