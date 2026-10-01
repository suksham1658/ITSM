package com.nbfc.itsm.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nbfc.itsm.audit.AuditRecorder;
import com.nbfc.itsm.config.ItsmProperties;
import com.nbfc.itsm.domain.ConfigChangeRequest;
import com.nbfc.itsm.domain.ConfigChangeRequestRepository;
import com.nbfc.itsm.domain.Employee;
import com.nbfc.itsm.domain.EmployeeRepository;
import com.nbfc.itsm.domain.SystemSetting;
import com.nbfc.itsm.domain.SystemSettingRepository;
import com.nbfc.itsm.exception.ItsmException;
import com.nbfc.itsm.security.ItsmUserPrincipal;
import com.nbfc.itsm.util.TimeUtc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Admin &gt; System Configuration: runtime-editable settings (LDAP, SMTP, session, workflow, general).
 * A System Administrator's change applies at once; anyone else's is a maker-checker request (Config approvals); once approved
 * the value is saved in {@code system_setting} and applied at once (no redeploy). Saved values override
 * application.yml and are re-applied at every start. Passwords are never stored here (the table forbids
 * secrets): they stay in the external secrets file.
 */
@Service
public class SystemSettingsService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SystemSettingsService.class);
    public static final String CHANGE_TYPE = "SETTING_UPDATE";
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    /** Section order and titles on the page. */
    public static final Map<String, String> CATEGORIES = new LinkedHashMap<String, String>();

    static {
        CATEGORIES.put("ldap", "LDAP / Active Directory Settings");
        CATEGORIES.put("smtp", "SMTP (E-mail) Settings");
        CATEGORIES.put("session", "Session Settings");
        CATEGORIES.put("workflow", "Workflow & Approval Settings");
        CATEGORIES.put("general", "General Settings");
    }

    private final Map<String, Def> defs = new LinkedHashMap<String, Def>();
    private final SystemSettingRepository settingRepository;
    private final ConfigChangeRequestRepository changeRepository;
    private final EmployeeRepository employeeRepository;
    private final AuditRecorder auditRecorder;
    private final ItsmProperties props;
    private final JavaMailSender mailSender;
    private final ObjectMapper objectMapper;
    private final String secretsLocation;
    private final int defaultIdleMinutes;

    /** Company name shown on the login page (general.company-name). */
    private volatile String companyName = "Authum";
    /** Idle timeout for new sessions, minutes; 0 = server default. */
    private volatile int idleMinutes;

    public SystemSettingsService(SystemSettingRepository settingRepository, ConfigChangeRequestRepository changeRepository,
                                 EmployeeRepository employeeRepository, AuditRecorder auditRecorder, ItsmProperties props,
                                 JavaMailSender mailSender, ObjectMapper objectMapper,
                                 @Value("${ITSM_CONFIG_DIR:D:/itsm-config}") String configDir,
                                 @Value("${server.servlet.session.timeout:30m}") Duration sessionTimeout) {
        this.settingRepository = settingRepository;
        this.changeRepository = changeRepository;
        this.employeeRepository = employeeRepository;
        this.auditRecorder = auditRecorder;
        this.props = props;
        this.mailSender = mailSender;
        this.objectMapper = objectMapper;
        this.secretsLocation = configDir.replace('/', '\\') + "\\itsm-secrets.yml";
        this.defaultIdleMinutes = (int) Math.max(1, sessionTimeout.toMinutes());
        this.idleMinutes = defaultIdleMinutes;
        define();
    }

    private void define() {
        final ItsmProperties.Ldap ldap = props.getLdap();
        text("ldap", "ldap.url", "LDAP server URL", "Domain controller, e.g. ldap://AUTHPDC.Authum.local:389 (ldaps:// for TLS).",
                ldap::getUrl, ldap::setUrl, "ldap");
        text("ldap", "ldap.base-dn", "Search base DN", "Where users are searched, e.g. dc=Authum,dc=local.",
                ldap::getBaseDn, ldap::setBaseDn, null);
        text("ldap", "ldap.bind-dn", "Service account", "Account used to look users up and unlock AD accounts. Its password is in the secrets file.",
                ldap::getBindDn, ldap::setBindDn, null);
        text("ldap", "ldap.user-search-filter", "User search filter", "{0} is the login ID, e.g. (sAMAccountName={0}).",
                ldap::getUserSearchFilter, ldap::setUserSearchFilter, "filter");
        text("ldap", "ldap.account-search-filter", "AD Account Unlock search filter", "{0} is the text typed on the unlock page.",
                ldap::getAccountSearchFilter, ldap::setAccountSearchFilter, "filter");
        text("ldap", "ldap.employee-id-attribute", "Employee ID attribute", "AD attribute holding the employee number, e.g. employeeID.",
                ldap::getEmployeeIdAttribute, ldap::setEmployeeIdAttribute, "word");
        number("ldap", "ldap.connect-timeout-ms", "Connect timeout (ms)", "How long to wait for the domain controller.", 500, 60000,
                () -> ldap.getConnectTimeoutMs(), v -> ldap.setConnectTimeoutMs(v));
        number("ldap", "ldap.read-timeout-ms", "Read timeout (ms)", "How long to wait for an LDAP answer.", 500, 120000,
                () -> ldap.getReadTimeoutMs(), v -> ldap.setReadTimeoutMs(v));

        final ItsmProperties.Mail mail = props.getMail();
        bool("smtp", "mail.enabled", "Send e-mails", "Ticket e-mails (created, closed, in your queue). Off = no e-mail is sent.",
                mail::isEnabled, mail::setEnabled);
        text("smtp", "mail.host", "SMTP server", "Company mail relay, e.g. 10.65.8.64.",
                this::mailHost, v -> withMailSender(s -> s.setHost(v)), "host");
        number("smtp", "mail.port", "SMTP port", "25 for the internal relay (no login, no TLS).", 1, 65535,
                this::mailPort, v -> withMailSender(s -> s.setPort(v)));
        text("smtp", "mail.from", "Sender address", "From address of ticket e-mails.", mail::getFrom, mail::setFrom, "email");
        text("smtp", "mail.from-name", "Sender name", "Shown as the sender, e.g. IT Service Desk.", mail::getFromName, mail::setFromName, null);
        text("smtp", "mail.portal-url", "Portal address for links", "Adds \"Open the ticket\" links, e.g. http://10.65.x.x:8090/itsm-portal. Blank = no link.",
                mail::getPortalUrl, mail::setPortalUrl, "url-optional");

        number("session", "session.idle-timeout-minutes", "Idle timeout (minutes)", "Signed out after this long without a click (new sessions).",
                5, 480, () -> idleMinutes, v -> idleMinutes = v);
        number("session", "session.absolute-timeout-hours", "Maximum session length (hours)", "Signed out after this long even when active.",
                1, 24, () -> (int) props.getSecurity().getSessionAbsoluteTimeout().toHours(),
                v -> props.getSecurity().setSessionAbsoluteTimeout(Duration.ofHours(v)));

        stored("workflow", "approval.remarks-min-length", "Minimum remarks length", "Characters required on Approve / Reject / Send back.", 1, 200, "10");
        stored("workflow", "workflow.max-manager-hops", "Maximum manager steps", "Cap on reporting-line approvals per request.", 1, 20, "12");
        storedBool("workflow", "workflow.requester-confirmation", "Requester confirmation before closing",
                "Off = a ticket closes as soon as the implementor resolves it. On = the requester must confirm first.", "false");

        text("general", "general.company-name", "Company name", "Shown on the login page.", () -> companyName, v -> companyName = v, null);
    }

    // ------------------------------------------------------------------ start-up

    /** Re-applies saved values after start (the table exists by now: SchemaInstaller / Flyway ran first). */
    @Override
    @Transactional(readOnly = true)
    public void run(ApplicationArguments args) {
        try {
            int n = 0;
            for (SystemSetting s : settingRepository.findAll()) {
                Def d = defs.get(s.getSettingKey());
                if (d != null && d.applier != null) {
                    d.applier.accept(s.getSettingValue());
                    n++;
                }
            }
            log.info("System settings: {} saved value(s) applied.", n);
        } catch (RuntimeException ex) {
            log.warn("System settings could not be loaded; using application.yml values: {}", ex.toString());
        }
    }

    // ------------------------------------------------------------------ page

    @PreAuthorize("hasAuthority('ADMIN_MASTERDATA_PROPOSE')")
    @Transactional(readOnly = true)
    public List<Row> rows() {
        Map<String, String> pending = new LinkedHashMap<String, String>();
        for (ConfigChangeRequest c : changeRepository.findByStatusCodeOrderByRequestedAtUtcDesc("PendingApproval")) {
            if (CHANGE_TYPE.equals(c.getChangeType())) {
                pending.put(c.getEntityKey(), valueOf(c.getPayloadJson()));
            }
        }
        List<Row> out = new ArrayList<Row>();
        for (Def d : defs.values()) {
            out.add(new Row(d, current(d), settingRepository.existsById(d.key), pending.get(d.key)));
        }
        return out;
    }

    /** Read-only facts for the Security section (nothing secret is shown). */
    public List<String[]> securityFacts() {
        List<String[]> f = new ArrayList<String[]>();
        f.add(new String[] {"Passwords (database, LDAP service account)", "Kept only in " + secretsLocation
                + " on the server, never in the database or this page. Change = edit the file + restart."});
        f.add(new String[] {"LDAP service account password", StringUtils.hasText(props.getLdap().getBindPassword()) ? "Set" : "Not set"});
        f.add(new String[] {"First System Administrator bootstrap (ITSM_BOOTSTRAP_ADMINS)",
                props.getSecurity().getBootstrapAdmins() == null || props.getSecurity().getBootstrapAdmins().isEmpty()
                        ? "Off" : "ON — remove it from setenv.bat once set-up is done"});
        f.add(new String[] {"Role changes and re-activation", "Always approved by a second administrator"});
        f.add(new String[] {"Audit trail", "Write-once; the database rejects changes or deletions"});
        return f;
    }

    public String getCompanyName() {
        return companyName;
    }

    public int getIdleMinutes() {
        return idleMinutes;
    }

    // ------------------------------------------------------------------ maker-checker

    @PreAuthorize("hasAuthority('ADMIN_MASTERDATA_PROPOSE')")
    @Transactional
    public ConfigChangeRequest propose(String key, String rawValue, ItsmUserPrincipal maker) {
        Def d = defs.get(key);
        if (d == null) {
            throw new ItsmException("SETTING_UNKNOWN", "Unknown setting " + key + ".");
        }
        String value = d.validate(rawValue == null ? "" : rawValue.trim());
        String now = current(d);
        if (value.equals(now == null ? "" : now)) {
            throw new ItsmException("SETTING_UNCHANGED", d.label + " already has this value.");
        }
        for (ConfigChangeRequest c : changeRepository.findByStatusCodeOrderByRequestedAtUtcDesc("PendingApproval")) {
            if (CHANGE_TYPE.equals(c.getChangeType()) && key.equals(c.getEntityKey())) {
                throw new ItsmException("ALREADY_PENDING", "A change to " + d.label + " is already waiting for approval.");
            }
        }
        Employee makerEmp = employeeRepository.findById(maker.getEmployeeId())
                .orElseThrow(() -> new ItsmException("EMPLOYEE_NOT_FOUND", "Employee not found."));
        ConfigChangeRequest ccr = new ConfigChangeRequest();
        ccr.setChangeType(CHANGE_TYPE);
        ccr.setEntityName("system_setting");
        ccr.setEntityKey(key);
        ccr.setPayloadJson(json(key, value));
        ccr.setPreviousJson(json(key, now == null ? "" : now));
        ccr.setDescription("Setting " + key + ": '" + shorten(now) + "' -> '" + shorten(value) + "'");
        ccr.setStatusCode("PendingApproval");
        ccr.setRequestedBy(makerEmp);
        ccr.setRequestedAtUtc(TimeUtc.now());
        if (AdminUserService.isSystemAdministrator(maker)) {
            // Final authority: applied at once, recorded as Applied (maker = reviewer).
            apply(ccr);
            ccr.setStatusCode("Applied");
            ccr.setReviewedBy(makerEmp);
            ccr.setReviewedAtUtc(TimeUtc.now());
            ccr.setDescription(ccr.getDescription() + " (applied by System Administrator)");
            changeRepository.save(ccr);
            return ccr;
        }
        changeRepository.save(ccr);
        auditRecorder.record("CONFIG", "PROPOSE", ccr.getDescription(), "SUCCESS");
        return ccr;
    }

    /** Called by {@link AdminUserService#approve} once a different administrator approved the request. */
    @Transactional
    public void apply(ConfigChangeRequest ccr) {
        Def d = defs.get(ccr.getEntityKey());
        if (d == null) {
            throw new ItsmException("SETTING_UNKNOWN", "Unknown setting " + ccr.getEntityKey() + ".");
        }
        String value = d.validate(valueOf(ccr.getPayloadJson()));
        SystemSetting s = settingRepository.findById(d.key).orElseGet(SystemSetting::new);
        s.setSettingKey(d.key);
        s.setSettingValue(value);
        s.setCategory(d.category);
        s.setDescription(d.label.length() > 256 ? d.label.substring(0, 256) : d.label);
        s.setSecret(false);
        settingRepository.save(s);
        if (d.applier != null) {
            d.applier.accept(value);
        }
        auditRecorder.record("CONFIG", "SETTING_APPLIED", d.key + " = '" + shorten(value) + "'", "SUCCESS");
    }

    public static boolean handles(ConfigChangeRequest ccr) {
        return CHANGE_TYPE.equals(ccr.getChangeType());
    }

    // ------------------------------------------------------------------ helpers

    private String current(Def d) {
        if (d.getter != null) {
            Object v = d.getter.get();
            return v == null ? "" : String.valueOf(v);
        }
        return settingRepository.findById(d.key).map(SystemSetting::getSettingValue).orElse(d.defaultValue);
    }

    private String mailHost() {
        return mailSender instanceof JavaMailSenderImpl ? ((JavaMailSenderImpl) mailSender).getHost() : "";
    }

    private int mailPort() {
        return mailSender instanceof JavaMailSenderImpl ? ((JavaMailSenderImpl) mailSender).getPort() : 25;
    }

    private void withMailSender(Consumer<JavaMailSenderImpl> change) {
        if (mailSender instanceof JavaMailSenderImpl) {
            change.accept((JavaMailSenderImpl) mailSender);
        }
    }

    private String json(String key, String value) {
        return objectMapper.createObjectNode().put("key", key).put("value", value).toString();
    }

    private String valueOf(String payload) {
        try {
            JsonNode n = objectMapper.readTree(payload);
            return n.path("value").asText("");
        } catch (Exception ex) {
            throw new ItsmException("SETTING_PAYLOAD", "Unreadable setting change.");
        }
    }

    private static String shorten(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > 120 ? s.substring(0, 119) + "…" : s;
    }

    private void text(String cat, String key, String label, String help, Supplier<Object> get, Consumer<String> set, String kind) {
        defs.put(key, new Def(cat, key, label, help, "text", kind, 0, 0, get, set, null));
    }

    private void number(String cat, String key, String label, String help, int min, int max, Supplier<Object> get,
                        Consumer<Integer> set) {
        defs.put(key, new Def(cat, key, label, help, "number", null, min, max, get, v -> set.accept(Integer.valueOf(v)), null));
    }

    private void bool(String cat, String key, String label, String help, Supplier<Object> get, Consumer<Boolean> set) {
        defs.put(key, new Def(cat, key, label, help, "bool", null, 0, 0, get, v -> set.accept(Boolean.valueOf(v)), null));
    }

    /** Values the application reads straight from system_setting (workflow engine, SLA). */
    private void stored(String cat, String key, String label, String help, int min, int max, String dflt) {
        defs.put(key, new Def(cat, key, label, help, "number", null, min, max, null, null, dflt));
    }

    private void storedBool(String cat, String key, String label, String help, String dflt) {
        defs.put(key, new Def(cat, key, label, help, "bool", null, 0, 0, null, null, dflt));
    }

    /** One setting: where it is shown, how it is checked, how it is read and applied. */
    public static final class Def {
        final String category;
        final String key;
        final String label;
        final String help;
        final String type;
        final String kind;
        final int min;
        final int max;
        final Supplier<Object> getter;
        final Consumer<String> applier;
        final String defaultValue;

        Def(String category, String key, String label, String help, String type, String kind, int min, int max,
            Supplier<Object> getter, Consumer<String> applier, String defaultValue) {
            this.category = category;
            this.key = key;
            this.label = label;
            this.help = help;
            this.type = type;
            this.kind = kind;
            this.min = min;
            this.max = max;
            this.getter = getter;
            this.applier = applier;
            this.defaultValue = defaultValue;
        }

        String validate(String v) {
            if ("bool".equals(type)) {
                if (!Arrays.asList("true", "false").contains(v)) {
                    throw new ItsmException("VALIDATION", label + " must be on or off.");
                }
                return v;
            }
            if ("number".equals(type)) {
                int n;
                try {
                    n = Integer.parseInt(v);
                } catch (NumberFormatException ex) {
                    throw new ItsmException("VALIDATION", label + " must be a whole number.");
                }
                if (n < min || n > max) {
                    throw new ItsmException("VALIDATION", label + " must be between " + min + " and " + max + ".");
                }
                return String.valueOf(n);
            }
            if (v.length() > 512) {
                throw new ItsmException("VALIDATION", label + " must be at most 512 characters.");
            }
            if (v.isEmpty() && !"url-optional".equals(kind)) {
                throw new ItsmException("VALIDATION", label + " is required.");
            }
            if ("ldap".equals(kind) && !(v.startsWith("ldap://") || v.startsWith("ldaps://"))) {
                throw new ItsmException("VALIDATION", label + " must start with ldap:// or ldaps://.");
            }
            if ("filter".equals(kind) && !(v.startsWith("(") && v.endsWith(")") && v.contains("{0}"))) {
                throw new ItsmException("VALIDATION", label + " must be an LDAP filter in brackets containing {0}.");
            }
            if ("word".equals(kind) && !v.matches("[A-Za-z][A-Za-z0-9-]*")) {
                throw new ItsmException("VALIDATION", label + " must be a single attribute name.");
            }
            if ("host".equals(kind) && !v.matches("[A-Za-z0-9.-]+")) {
                throw new ItsmException("VALIDATION", label + " must be a host name or IP address.");
            }
            if ("email".equals(kind) && !EMAIL.matcher(v).matches()) {
                throw new ItsmException("VALIDATION", label + " must be an e-mail address.");
            }
            if ("url-optional".equals(kind) && !v.isEmpty() && !(v.startsWith("http://") || v.startsWith("https://"))) {
                throw new ItsmException("VALIDATION", label + " must start with http:// or https://.");
            }
            return v;
        }
    }

    /** One line on the page. */
    public static final class Row {
        private final Def def;
        private final String value;
        private final boolean saved;
        private final String pendingValue;

        Row(Def def, String value, boolean saved, String pendingValue) {
            this.def = def;
            this.value = value;
            this.saved = saved;
            this.pendingValue = pendingValue;
        }

        public String getCategory() { return def.category; }
        public String getKey() { return def.key; }
        public String getLabel() { return def.label; }
        public String getHelp() { return def.help; }
        public String getType() { return def.type; }
        public int getMin() { return def.min; }
        public int getMax() { return def.max; }
        public String getValue() { return value; }
        /** True when the value comes from the database (set on this page) rather than application.yml. */
        public boolean isSaved() { return saved; }
        public String getPendingValue() { return pendingValue; }
    }
}
