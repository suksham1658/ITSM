package com.nbfc.itsm.web;

import com.nbfc.itsm.domain.ConfigChangeRequestRepository;
import com.nbfc.itsm.domain.Notification;
import com.nbfc.itsm.domain.NotificationRepository;
import com.nbfc.itsm.security.ItsmUserPrincipal;
import com.nbfc.itsm.validation.FieldLimits;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.Collections;
import java.util.List;

@ControllerAdvice
public class UiModelAdvice {

    private final ConfigChangeRequestRepository configChangeRequestRepository;
    private final NotificationRepository notificationRepository;
    private final com.nbfc.itsm.admin.SystemSettingsService systemSettings;
    private final com.nbfc.itsm.license.LicenseService licenseService;

    public UiModelAdvice(ConfigChangeRequestRepository configChangeRequestRepository,
                         NotificationRepository notificationRepository,
                         com.nbfc.itsm.admin.SystemSettingsService systemSettings,
                         com.nbfc.itsm.license.LicenseService licenseService) {
        this.configChangeRequestRepository = configChangeRequestRepository;
        this.notificationRepository = notificationRepository;
        this.systemSettings = systemSettings;
        this.licenseService = licenseService;
    }

    /**
     * License banner, shown only to a System Administrator and only once the license is in its grace period or
     * has lapsed. Null (nothing shown) during a valid term, and null for every other role.
     */
    @org.springframework.beans.factory.annotation.Value("${itsm.license.enforce:true}")
    private boolean licenseEnforce;

    @ModelAttribute("licenseNotice")
    public String licenseNotice(org.springframework.security.core.Authentication authentication) {
        if (!licenseEnforce) {
            return null;
        }
        boolean sysAdmin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_SYSTEM_ADMINISTRATOR".equals(a.getAuthority()));
        if (!sysAdmin) {
            return null;
        }
        com.nbfc.itsm.license.LicenseState state = licenseService.current();
        return state.isOperational() && !state.isGrace() ? null : state.sysAdminNotice();
    }

    /** System Configuration &gt; General &gt; Company name (login page). */
    @ModelAttribute("companyName")
    public String companyName() {
        return systemSettings.getCompanyName();
    }

    /** Sidebar badge on Config approvals; only counted for people who see that menu entry. */
    @ModelAttribute("pendingConfigCount")
    public long pendingConfigCount(org.springframework.security.core.Authentication authentication) {
        boolean approver = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> "ADMIN_MASTERDATA_APPROVE".equals(a.getAuthority()));
        return approver ? configChangeRequestRepository.countByStatusCode("PendingApproval") : 0;
    }

    /** Field limits shared with the Java validation, for required/minlength/maxlength in forms. */
    @ModelAttribute("limits")
    public FieldLimits limits() {
        return FieldLimits.get();
    }

    /** Unread count for the header bell (0 when signed out). */
    @ModelAttribute("notifUnread")
    public long notifUnread(org.springframework.security.core.Authentication authentication) {
        ItsmUserPrincipal user = currentUser(authentication);
        return user == null ? 0 : notificationRepository.countByRecipientIdAndReadFalse(user.getEmployeeId());
    }

    /** Latest notifications for the header bell dropdown. */
    @ModelAttribute("notifLatest")
    public List<Notification> notifLatest(org.springframework.security.core.Authentication authentication) {
        ItsmUserPrincipal user = currentUser(authentication);
        return user == null ? Collections.<Notification>emptyList()
                : notificationRepository.findTop8ByRecipientIdOrderByCreatedAtUtcDescNotificationIdDesc(user.getEmployeeId());
    }

    @ModelAttribute("currentUser")
    public ItsmUserPrincipal currentUser(org.springframework.security.core.Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof ItsmUserPrincipal) {
            return (ItsmUserPrincipal) authentication.getPrincipal();
        }
        return null;
    }
}
