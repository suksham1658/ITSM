package com.nbfc.itsm.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    Optional<Employee> findByEmployeeNo(String employeeNo);

    Optional<Employee> findBySamAccountNameIgnoreCase(String samAccountName);

    List<Employee> findByDisplayNameContainingIgnoreCaseOrEmployeeNoContainingIgnoreCaseOrSamAccountNameContainingIgnoreCase(
            String displayName, String employeeNo, String sam);

    long countByPortalActive(boolean portalActive);

    List<Employee> findByPortalActiveTrueOrderByDisplayNameAsc();
}
