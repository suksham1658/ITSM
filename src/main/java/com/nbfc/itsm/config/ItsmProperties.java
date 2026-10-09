package com.nbfc.itsm.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "itsm")
public class ItsmProperties {

    private final Ldap ldap = new Ldap();
    private final Storage storage = new Storage();
    private final Mail mail = new Mail();
    private final Security security = new Security();

    public Ldap getLdap() {
        return ldap;
    }

    public Storage getStorage() {
        return storage;
    }

    public Mail getMail() {
        return mail;
    }

    public Security getSecurity() {
        return security;
    }

    public static class Ldap {
        private String url = "";
        private String baseDn = "";
        private String bindDn = "";
        private String bindPassword = "";
        private String userSearchFilter = "(sAMAccountName={0})";
        private String userDnPattern = "";
        private String employeeIdAttribute = "employeeID";
        private String managerAttribute = "manager";
        /** Unlock page search; {0} is the (escaped) text typed by the Service Desk. Prefix match on the ID, contains on the name. */
        private String accountSearchFilter =
                "(&(objectCategory=person)(objectClass=user)(|(sAMAccountName={0}*)(employeeID={0})(displayName=*{0}*)))";
        private int connectTimeoutMs = 3000;
        private int readTimeoutMs = 5000;

        public boolean isConfigured() {
            return url != null && url.trim().length() > 0;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getBaseDn() {
            return baseDn;
        }

        public void setBaseDn(String baseDn) {
            this.baseDn = baseDn;
        }

        public String getBindDn() {
            return bindDn;
        }

        public void setBindDn(String bindDn) {
            this.bindDn = bindDn;
        }

        public String getBindPassword() {
            return bindPassword;
        }

        public void setBindPassword(String bindPassword) {
            this.bindPassword = bindPassword;
        }

        public String getUserSearchFilter() {
            return userSearchFilter;
        }

        public void setUserSearchFilter(String userSearchFilter) {
            this.userSearchFilter = userSearchFilter;
        }

        public String getUserDnPattern() {
            return userDnPattern;
        }

        public void setUserDnPattern(String userDnPattern) {
            this.userDnPattern = userDnPattern;
        }

        public String getEmployeeIdAttribute() {
            return employeeIdAttribute;
        }

        public void setEmployeeIdAttribute(String employeeIdAttribute) {
            this.employeeIdAttribute = employeeIdAttribute;
        }

        public String getManagerAttribute() {
            return managerAttribute;
        }

        public void setManagerAttribute(String managerAttribute) {
            this.managerAttribute = managerAttribute;
        }

        public String getAccountSearchFilter() {
            return accountSearchFilter;
        }

        public void setAccountSearchFilter(String accountSearchFilter) {
            this.accountSearchFilter = accountSearchFilter;
        }

        public int getConnectTimeoutMs() {
            return connectTimeoutMs;
        }

        public void setConnectTimeoutMs(int connectTimeoutMs) {
            this.connectTimeoutMs = connectTimeoutMs;
        }

        public int getReadTimeoutMs() {
            return readTimeoutMs;
        }

        public void setReadTimeoutMs(int readTimeoutMs) {
            this.readTimeoutMs = readTimeoutMs;
        }
    }

    public static class Storage {
        private String root = "";
        private String type = "filesystem";

        public String getRoot() {
            return root;
        }

        public void setRoot(String root) {
            this.root = root;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }
    }

    /** Ticket e-mails to the requester (created / closed). SMTP server itself is spring.mail.*. */
    public static class Mail {
        private boolean enabled = false;
        private String from = "";
        private String fromName = "IT Service Desk";
        /** Portal address for the "Open ticket" link, e.g. http://10.65.x.x:8090/itsm-portal (blank: no link). */
        private String portalUrl = "";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getFrom() {
            return from;
        }

        public void setFrom(String from) {
            this.from = from;
        }

        public String getFromName() {
            return fromName;
        }

        public void setFromName(String fromName) {
            this.fromName = fromName;
        }

        public String getPortalUrl() {
            return portalUrl;
        }

        public void setPortalUrl(String portalUrl) {
            this.portalUrl = portalUrl;
        }
    }

    public static class Security {
        private boolean testLoginEnabled = false;
        /** Idle timeout is server.servlet.session.timeout. This is absolute max session age. */
        private java.time.Duration sessionAbsoluteTimeout = java.time.Duration.ofHours(8);

        public boolean isTestLoginEnabled() {
            return testLoginEnabled;
        }

        public void setTestLoginEnabled(boolean testLoginEnabled) {
            this.testLoginEnabled = testLoginEnabled;
        }

        /** H2 preview only. Never used in uat/prod. Sourced from H2_PREVIEW_PASSWORD. */
        private String previewPassword = "";

        /**
         * First-run bootstrap: AD usernames (sAMAccountName) or employee numbers that become System
         * Administrator on login, but only while no active System Administrator exists
         * (ITSM_BOOTSTRAP_ADMINS, comma separated).
         */
        private java.util.List<String> bootstrapAdmins = new java.util.ArrayList<String>();

        public java.util.List<String> getBootstrapAdmins() {
            return bootstrapAdmins;
        }

        public void setBootstrapAdmins(java.util.List<String> bootstrapAdmins) {
            this.bootstrapAdmins = bootstrapAdmins == null ? new java.util.ArrayList<String>() : bootstrapAdmins;
        }

        public String getPreviewPassword() {
            return previewPassword;
        }

        public void setPreviewPassword(String previewPassword) {
            this.previewPassword = previewPassword;
        }

        public java.time.Duration getSessionAbsoluteTimeout() {
            return sessionAbsoluteTimeout;
        }

        public void setSessionAbsoluteTimeout(java.time.Duration sessionAbsoluteTimeout) {
            this.sessionAbsoluteTimeout = sessionAbsoluteTimeout;
        }
    }
}
