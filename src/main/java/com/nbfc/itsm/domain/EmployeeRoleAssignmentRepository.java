package com.nbfc.itsm.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface EmployeeRoleAssignmentRepository extends JpaRepository<EmployeeRoleAssignment, EmployeeRoleId> {

    Optional<EmployeeRoleAssignment> findByEmployeeAndRole(Employee employee, Role role);

    void deleteByEmployeeAndRole(Employee employee, Role role);

    List<EmployeeRoleAssignment> findByRole(Role role);

    List<EmployeeRoleAssignment> findByEmployee(Employee employee);

    @Query("select a.role.roleId, count(a) from EmployeeRoleAssignment a group by a.role.roleId")
    List<Object[]> countHoldersByRole();
}
