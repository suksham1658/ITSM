package com.nbfc.itsm.web;

import com.nbfc.itsm.domain.ConfigChangeRequestRepository;
import com.nbfc.itsm.domain.EmployeeRepository;
import com.nbfc.itsm.security.ItsmUserPrincipal;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class UiModelAdvice {

    private final EmployeeRepository employeeRepository;
    private final ConfigChangeRequestRepository configChangeRequestRepository;

    public UiModelAdvice(EmployeeRepository employeeRepository,
                         ConfigChangeRequestRepository configChangeRequestRepository) {
        this.employeeRepository = employeeRepository;
        this.configChangeRequestRepository = configChangeRequestRepository;
    }

    @ModelAttribute("pendingConfigCount")
    public long pendingConfigCount() {
        return configChangeRequestRepository.countByStatusCode("PendingApproval");
    }

    @ModelAttribute("employeeCount")
    public long employeeCount() {
        return employeeRepository.count();
    }

    @ModelAttribute("currentUser")
    public ItsmUserPrincipal currentUser(org.springframework.security.core.Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof ItsmUserPrincipal) {
            return (ItsmUserPrincipal) authentication.getPrincipal();
        }
        return null;
    }
}
