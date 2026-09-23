package com.nbfc.itsm.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AssignmentGroupRepository extends JpaRepository<AssignmentGroup, Long> {
    Optional<AssignmentGroup> findByCode(String code);
    List<AssignmentGroup> findByActiveTrueOrderByNameAsc();
}
