package com.nbfc.itsm.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WorkflowInstanceRepository extends JpaRepository<WorkflowInstance, Long> {
    Optional<WorkflowInstance> findByTicketId(Long ticketId);

    long countByWorkflowDefinition(WorkflowDefinition definition);

    long countByWorkflowRule(WorkflowRule rule);
}
