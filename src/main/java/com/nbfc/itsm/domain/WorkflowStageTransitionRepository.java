package com.nbfc.itsm.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WorkflowStageTransitionRepository extends JpaRepository<WorkflowStageTransition, Long> {
    List<WorkflowStageTransition> findByWorkflowStage(WorkflowStage stage);
    Optional<WorkflowStageTransition> findByWorkflowStageAndActionCode(WorkflowStage stage, String actionCode);
}
