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
import java.util.Hashtable;

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
            return searchPerson(userCtx, username, ldap, userDn);
        } finally {
            closeQuietly(userCtx);
        }
    }

    private String resolveUserDn(String username, String password, ItsmProperties.Ldap ldap) throws NamingException {
        if (username.indexOf('=') >= 0 && username.indexOf(',') >= 0) {
            bind(username, password, ldap).close();
            return username;
        }
        if (StringUtils.hasText(ldap.getUserDnPattern())) {
            String dn = ldap.getUserDnPattern().replace("{0}", sanitize(username));
            bind(dn, password, ldap).close();
            return dn;
        }
        if (StringUtils.hasText(ldap.getBindDn()) && StringUtils.hasText(ldap.getBindPassword())) {
            DirContext service = bind(ldap.getBindDn(), ldap.getBindPassword(), ldap);
            try {
                String found = searchDn(service, username, ldap);
                if (!StringUtils.hasText(found)) {
                    throw new NamingException("User not found");
                }
                DirContext check = bind(found, password, ldap);
                closeQuietly(check);
                return found;
            } finally {
                closeQuietly(service);
            }
        }
        if (StringUtils.hasText(ldap.getBaseDn())) {
            String dn = "CN=" + sanitize(username) + "," + ldap.getBaseDn();
            bind(dn, password, ldap).close();
            return dn;
        }
        throw new NamingException("Cannot resolve user DN");
    }

    private DirContext bind(String principal, String password, ItsmProperties.Ldap ldap) throws NamingException {
        Hashtable<String, String> env = new Hashtable<String, String>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        env.put(Context.PROVIDER_URL, ldap.getUrl());
        env.put(Context.SECURITY_AUTHENTICATION, "simple");
        env.put(Context.SECURITY_PRINCIPAL, principal);
        env.put(Context.SECURITY_CREDENTIALS, password);
        env.put("com.sun.jndi.ldap.connect.timeout", String.valueOf(ldap.getConnectTimeoutMs()));
        env.put("com.sun.jndi.ldap.read.timeout", String.valueOf(ldap.getReadTimeoutMs()));
        return new InitialDirContext(env);
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
        sc.setReturningAttributes(new String[] {
                "sAMAccountName", "uid", ldap.getEmployeeIdAttribute(), "displayName", "cn", "mail",
                "title", "userPrincipalName", "givenName", "sn"
        });
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
