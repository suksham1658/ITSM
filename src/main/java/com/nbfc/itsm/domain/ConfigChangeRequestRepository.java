package com.nbfc.itsm.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConfigChangeRequestRepository extends JpaRepository<ConfigChangeRequest, Long> {

    List<ConfigChangeRequest> findByStatusCodeOrderByRequestedAtUtcDesc(String statusCode);

    List<ConfigChangeRequest> findAllByOrderByRequestedAtUtcDesc();

    long countByStatusCode(String statusCode);
}
