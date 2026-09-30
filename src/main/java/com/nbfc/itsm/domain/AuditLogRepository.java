package com.nbfc.itsm.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {

    @Query("select distinct a.moduleCode from AuditLog a order by a.moduleCode")
    List<String> distinctModules();

    @Query("select distinct a.actionCode from AuditLog a order by a.actionCode")
    List<String> distinctActions();
}
