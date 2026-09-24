package com.nbfc.itsm.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WorkflowInstanceStageRepository extends JpaRepository<WorkflowInstanceStage, Long> {
    List<WorkflowInstanceStage> findByWorkflowInstanceOrderByStageOrderAsc(WorkflowInstance instance);
    Optional<WorkflowInstanceStage> findByWorkflowInstanceAndStatusCode(WorkflowInstance instance, String statusCode);
    List<WorkflowInstanceStage> findByStatusCode(String statusCode);

    List<WorkflowInstanceStage> findByStatusCodeAndStageType(String statusCode, String stageType);

    boolean existsByResolvedRole(Role role);
}
