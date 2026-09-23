package com.nbfc.itsm.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TicketRepository extends JpaRepository<Ticket, Long>, JpaSpecificationExecutor<Ticket> {

    long countByStatusCodeNotIn(List<String> statuses);

    List<Ticket> findTop8ByRequesterOrderByCreatedAtUtcDesc(Employee requester);

    @Query("select t from Ticket t where t.requester = :requester order by t.createdAtUtc desc")
    Page<Ticket> findMine(@Param("requester") Employee requester, Pageable pageable);
}
