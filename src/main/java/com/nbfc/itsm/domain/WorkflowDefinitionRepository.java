package com.nbfc.itsm.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WorkflowDefinitionRepository extends JpaRepository<WorkflowDefinition, Long> {
    Optional<WorkflowDefinition> findByCodeAndVersionNo(String code, int versionNo);
    List<WorkflowDefinition> findAllByOrderByCodeAscVersionNoAsc();

    List<WorkflowDefinition> findByCodeOrderByVersionNoDesc(String code);

    List<WorkflowDefinition> findByStatusCodeOrderByCodeAsc(String statusCode);
}
