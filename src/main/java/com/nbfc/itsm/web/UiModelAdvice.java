package com.nbfc.itsm.web;

import com.nbfc.itsm.domain.ConfigChangeRequestRepository;
import com.nbfc.itsm.domain.EmployeeRepository;
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

    private final EmployeeRepository employeeRepository;
    private final ConfigChangeRequestRepository configChangeRequestRepository;
    private final NotificationRepository notificationRepository;

    public UiModelAdvice(EmployeeRepository employeeRepository,
                         ConfigChangeRequestRepository configChangeRequestRepository,
                         NotificationRepository notificationRepository) {
        this.employeeRepository = employeeRepository;
        this.configChangeRequestRepository = configChangeRequestRepository;
        this.notificationRepository = notificationRepository;
    }

    @ModelAttribute("pendingConfigCount")
    public long pendingConfigCount() {
        return configChangeRequestRepository.countByStatusCode("PendingApproval");
    }

    @ModelAttribute("employeeCount")
    public long employeeCount() {
        return employeeRepository.count();
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
