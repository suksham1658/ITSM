package com.nbfc.itsm.admin;

import com.nbfc.itsm.audit.AuditRecorder;
import com.nbfc.itsm.config.ItsmProperties;
import com.nbfc.itsm.domain.Employee;
import com.nbfc.itsm.domain.EmployeeRepository;
import com.nbfc.itsm.exception.ItsmException;
import com.nbfc.itsm.identity.AdAccountStatus;
import com.nbfc.itsm.identity.LdapDirectoryClient;
import com.nbfc.itsm.notification.NotificationService;
import com.nbfc.itsm.security.ItsmUserPrincipal;
import com.nbfc.itsm.validation.FieldLimits;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.naming.NamingException;
import javax.naming.NoPermissionException;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * IT Service Desk / System Administrator unlock of locked-out Active Directory accounts
 * (permission {@code AD_ACCOUNT_UNLOCK}). The only directory change is {@code lockoutTime = 0},
 * written with the service account; passwords and enable/disable are never touched. Every
 * attempt is audited with the remarks.
 */
@Service
public class AdAccountUnlockService {

    private static final Logger log = LoggerFactory.getLogger(AdAccountUnlockService.class);

    public static final String PERMISSION = "AD_ACCOUNT_UNLOCK";
    public static final int QUERY_MIN = 2;
    public static final int QUERY_MAX = 64;
    public static final int SEARCH_LIMIT = 25;
    /** Login IDs and names: letters, digits, space and . _ - @ \ (the domain prefix). */
    private static final Pattern QUERY_CHARS = Pattern.compile("[\\p{L}\\p{N} ._@\\\\-]+");

    private final LdapDirectoryClient directory;
    private final ItsmProperties properties;
    private final EmployeeRepository employeeRepository;
    private final AuditRecorder auditRecorder;
    private final NotificationService notificationService;

    public AdAccountUnlockService(LdapDirectoryClient directory, ItsmProperties properties,
                                  EmployeeRepository employeeRepository, AuditRecorder auditRecorder,
                                  NotificationService notificationService) {
        this.directory = directory;
        this.properties = properties;
        this.employeeRepository = employeeRepository;
        this.auditRecorder = auditRecorder;
        this.notificationService = notificationService;
    }

    public List<AdAccountStatus> search(String query) {
        String q = validQuery(query);
        try {
            return directory.searchAccounts(q, SEARCH_LIMIT);
        } catch (NamingException ex) {
            throw new ItsmException("AD_SEARCH_FAILED", explain(ex));
        }
    }

    /** Status of one account; fails if it is not in the directory. */
    public AdAccountStatus status(String username) {
        String id = validQuery(username);
        try {
            AdAccountStatus s = directory.readAccount(id);
            if (s == null) {
                throw new ItsmException("AD_ACCOUNT_NOT_FOUND", "No directory account with login ID '" + id + "'.");
            }
            return s;
        } catch (NamingException ex) {
            throw new ItsmException("AD_SEARCH_FAILED", explain(ex));
        }
    }

    @Transactional
    public AdAccountStatus unlock(ItsmUserPrincipal actor, String username, String remarks) {
        String id = validQuery(username);
        String why = remarks == null ? "" : remarks.trim();
        if (why.length() < FieldLimits.REMARKS_MIN) {
            throw new ItsmException("VALIDATION",
                    "Remarks are required (at least " + FieldLimits.REMARKS_MIN + " characters), e.g. how the user's identity was verified.");
        }
        if (why.length() > FieldLimits.REMARKS_MAX) {
            throw new ItsmException("VALIDATION", "Remarks must be at most " + FieldLimits.REMARKS_MAX + " characters.");
        }
        AdAccountStatus before = status(id);
        if (!before.isLocked()) {
            throw new ItsmException("AD_NOT_LOCKED", before.getDisplayName() + " (" + id + ") is not locked out; nothing to unlock.");
        }
        String detail = "account=" + id + " name=" + before.getDisplayName() + " by=" + actor.getEmployeeNo()
                + " remarks=" + why;
        AdAccountStatus after;
        try {
            after = directory.unlock(id);
        } catch (NamingException ex) {
            String reason = explain(ex);
            auditRecorder.record("AD_ACCOUNT", "UNLOCK", detail + " error=" + reason, "FAILED");
            throw new ItsmException("AD_UNLOCK_FAILED", reason);
        }
        if (after == null || after.isLocked()) {
            auditRecorder.record("AD_ACCOUNT", "UNLOCK", detail + " error=still locked after write", "FAILED");
            throw new ItsmException("AD_UNLOCK_FAILED",
                    "The directory accepted the change but still reports the account as locked. Try again in a minute.");
        }
        auditRecorder.record("AD_ACCOUNT", "UNLOCK", detail, "SUCCESS");
        log.info("AD account {} unlocked by {}", id, actor.getEmployeeNo());
        Employee owner = portalEmployee(after);
        Employee by = employeeRepository.findById(actor.getEmployeeId()).orElse(null);
        if (owner != null && by != null && !owner.getEmployeeId().equals(by.getEmployeeId())) {
            notificationService.accountUnlocked(owner, by);
        }
        return after;
    }

    private Employee portalEmployee(AdAccountStatus s) {
        if (StringUtils.hasText(s.getSamAccountName())) {
            Employee e = employeeRepository.findBySamAccountNameIgnoreCase(s.getSamAccountName()).orElse(null);
            if (e != null) {
                return e;
            }
        }
        return StringUtils.hasText(s.getEmployeeNo()) ? employeeRepository.findByEmployeeNo(s.getEmployeeNo()).orElse(null) : null;
    }

    static String validQuery(String query) {
        String q = query == null ? "" : query.trim();
        if (q.length() < QUERY_MIN || q.length() > QUERY_MAX) {
            throw new ItsmException("VALIDATION",
                    "Enter " + QUERY_MIN + " to " + QUERY_MAX + " characters of the login ID, employee ID or name.");
        }
        if (!QUERY_CHARS.matcher(q).matches()) {
            throw new ItsmException("VALIDATION", "Use only letters, digits, spaces and . _ - @ \\ in the search.");
        }
        return q;
    }

    /** Message for the Service Desk: what failed and who can fix it. */
    String explain(NamingException ex) {
        String msg = String.valueOf(ex.getMessage());
        if (ex instanceof NoPermissionException || msg.contains("INSUFF_ACCESS_RIGHTS")
                || msg.toLowerCase(Locale.ROOT).contains("insufficient access")) {
            return "The portal's service account (" + properties.getLdap().getBindDn() + ") is not allowed to unlock "
                    + "accounts. Ask the AD team to delegate 'Read/Write lockoutTime' on the user OUs to it.";
        }
        if (msg.startsWith("Service account not configured") || msg.startsWith("LDAP is not configured")) {
            return "Account unlock needs the LDAP service account (LDAP_BIND_DN / LDAP_BIND_PASSWORD) to be configured.";
        }
        return "Active Directory error: " + LdapDirectoryClient.diagnose(ex);
    }
}
