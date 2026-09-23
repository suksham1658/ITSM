package com.nbfc.itsm.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AssignmentGroupMemberRepository extends JpaRepository<AssignmentGroupMember, AssignmentGroupMemberId> {
    List<AssignmentGroupMember> findByAssignmentGroup(AssignmentGroup group);
    List<AssignmentGroupMember> findByEmployee(Employee employee);
    boolean existsByAssignmentGroupAndEmployee(AssignmentGroup group, Employee employee);
}
