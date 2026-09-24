package com.nbfc.itsm.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkflowStageRepository extends JpaRepository<WorkflowStage, Long> {
    List<WorkflowStage> findByWorkflowDefinitionOrderByStageOrderAsc(WorkflowDefinition definition);

    boolean existsByRole(Role role);
}
