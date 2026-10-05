package com.nbfc.itsm.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ImacDetailRepository extends JpaRepository<ImacDetail, Long> {

    Optional<ImacDetail> findByTicketId(Long ticketId);
}
