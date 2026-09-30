package com.nbfc.itsm.admin;

import com.nbfc.itsm.audit.AuditRecorder;
import com.nbfc.itsm.domain.Employee;
import com.nbfc.itsm.domain.EmployeeRepository;
import com.nbfc.itsm.exception.ItsmException;
import com.nbfc.itsm.identity.LdapDirectoryClient;
import com.nbfc.itsm.identity.LdapPerson;
import com.nbfc.itsm.identity.PortalUserService;
import com.nbfc.itsm.security.ItsmUserPrincipal;
import com.nbfc.itsm.validation.Validation;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.naming.NamingException;
import java.util.regex.Pattern;

/**
 * Admin &gt; Users actions that apply immediately (as in the portal design):
 * <ul>
 *   <li>Basic information: profile data only (name, e-mail, designation), no change of access.</li>
 *   <li>Delegate (backup approver): may act on approvals resolved to the employee; grants no access by itself.</li>
 *   <li>Deactivate portal access: offboarding / security cannot wait for a second admin.</li>
 *   <li>Re-sync from LDAP: refreshes directory data; roles, delegate and portal access are kept.</li>
 * </ul>
 * Role changes and re-activation go to a second administrator ({@link AdminUserService}). All audited.
 */
@Service
public class UserAccountService {

    static final int NAME_MAX = 256;
    static final int EMAIL_MAX = 256;
    static final int DESIGNATION_MAX = 256;
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final EmployeeRepository employeeRepository;
    private final AdminUserService adminUserService;
    private final LdapDirectoryClient directory;
    private final PortalUserService portalUserService;
    private final AuditRecorder auditRecorder;

    public UserAccountService(EmployeeRepository employeeRepository, AdminUserService adminUserService,
                              LdapDirectoryClient directory, PortalUserService portalUserService,
                              AuditRecorder auditRecorder) {
        this.employeeRepository = employeeRepository;
        this.adminUserService = adminUserService;
        this.directory = directory;
        this.portalUserService = portalUserService;
        this.auditRecorder = auditRecorder;
    }

    @PreAuthorize("hasAuthority('ADMIN_USER_MANAGE')")
    @Transactional
    public void updateBasicInfo(Long employeeId, String name, String email, String designation, ItsmUserPrincipal actor) {
        Employee e = require(employeeId);
        Validation v = new Validation();
        String cleanName = v.text(name, "Full name", 2, NAME_MAX, true);
        String cleanEmail = v.text(email, "E-mail", 5, EMAIL_MAX, false);
        String cleanDesignation = v.text(designation, "Designation", 1, DESIGNATION_MAX, false);
        v.check(cleanEmail == null || EMAIL.matcher(cleanEmail).matches(), "Enter a valid e-mail address.");
        v.throwIfInvalid();
        String before = e.getDisplayName() + " | " + e.getEmail() + " | " + e.getDesignation();
        e.setDisplayName(cleanName);
        e.setEmail(cleanEmail);
        e.setDesignation(cleanDesignation);
        employeeRepository.save(e);
        auditRecorder.record("ADMIN", "USER_BASIC_INFO", e.getEmployeeNo() + ": " + before + " -> " + cleanName
                + " | " + cleanEmail + " | " + cleanDesignation + " (by " + actor.getEmployeeNo() + ")", "SUCCESS");
    }

    /** Sets or clears ({@code delegateId == null}) the backup approver. */
    @PreAuthorize("hasAuthority('ADMIN_USER_MANAGE')")
    @Transactional
    public void setDelegate(Long employeeId, Long delegateId, ItsmUserPrincipal actor) {
        Employee e = require(employeeId);
        Employee delegate = null;
        if (delegateId != null) {
            delegate = require(delegateId);
            if (delegate.getEmployeeId().equals(e.getEmployeeId())) {
                throw new ItsmException("VALIDATION", "An employee cannot be their own delegate.");
            }
            if (!delegate.isPortalActive()) {
                throw new ItsmException("VALIDATION", delegate.getDisplayName() + " has no portal access and cannot be a delegate.");
            }
        }
        String before = e.getDelegate() == null ? "none" : e.getDelegate().getEmployeeNo();
        e.setDelegate(delegate);
        employeeRepository.save(e);
        auditRecorder.record("ADMIN", "USER_DELEGATE", e.getEmployeeNo() + ": delegate " + before + " -> "
                + (delegate == null ? "none" : delegate.getEmployeeNo()) + " (by " + actor.getEmployeeNo() + ")", "SUCCESS");
    }

    /** Immediate: no second admin. Re-activation goes through {@link AdminUserService#proposePortalActive}. */
    @PreAuthorize("hasAuthority('ADMIN_USER_MANAGE')")
    @Transactional
    public void deactivate(Long employeeId, ItsmUserPrincipal actor) {
        Employee e = require(employeeId);
        if (e.getEmployeeId().equals(actor.getEmployeeId())) {
            throw new ItsmException("SELF_DEACTIVATE", "You cannot deactivate your own portal access.");
        }
        if (!e.isPortalActive()) {
            throw new ItsmException("ALREADY_INACTIVE", e.getDisplayName() + " is already deactivated.");
        }
        adminUserService.assertAdminsRemainWithoutEmployee(e);
        e.setPortalActive(false);
        employeeRepository.save(e);
        auditRecorder.record("ADMIN", "USER_DEACTIVATE", e.getEmployeeNo() + " portal access deactivated (by "
                + actor.getEmployeeNo() + ")", "SUCCESS");
    }

    /** Reads the employee from AD with the service account and applies it like a login would. */
    @PreAuthorize("hasAuthority('ADMIN_USER_MANAGE')")
    @Transactional
    public Employee resync(Long employeeId, ItsmUserPrincipal actor) {
        Employee e = require(employeeId);
        String login = StringUtils.hasText(e.getSamAccountName()) ? e.getSamAccountName() : e.getEmployeeNo();
        LdapPerson person;
        try {
            person = directory.loadPerson(login);
        } catch (NamingException ex) {
            auditRecorder.record("ADMIN", "USER_RESYNC", e.getEmployeeNo() + " failed: " + ex.getMessage(), "FAILED");
            throw new ItsmException("LDAP_RESYNC_FAILED", "Could not read " + login + " from the directory: "
                    + LdapDirectoryClient.diagnose(ex));
        }
        if (person == null) {
            throw new ItsmException("LDAP_NOT_FOUND", "No directory account '" + login + "' was found.");
        }
        portalUserService.resyncFromDirectory(e, person);
        auditRecorder.record("ADMIN", "USER_RESYNC", e.getEmployeeNo() + " re-synced from LDAP (by "
                + actor.getEmployeeNo() + ")", "SUCCESS");
        return e;
    }

    private Employee require(Long id) {
        return employeeRepository.findById(id)
                .orElseThrow(() -> new ItsmException("EMPLOYEE_NOT_FOUND", "Employee not found."));
    }
}
