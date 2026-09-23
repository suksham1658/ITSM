package com.nbfc.itsm.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WorkflowRuleRepository extends JpaRepository<WorkflowRule, Long> {
    List<WorkflowRule> findByStatusCodeOrderByPriorityAsc(String statusCode);
    Optional<WorkflowRule> findByPriorityAndStatusCode(int priority, String statusCode);
}
