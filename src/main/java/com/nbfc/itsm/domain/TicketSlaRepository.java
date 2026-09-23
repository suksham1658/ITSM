package com.nbfc.itsm.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TicketSlaRepository extends JpaRepository<TicketSla, Long> {
    Optional<TicketSla> findByTicket(Ticket ticket);
    List<TicketSla> findByStateCodeIn(List<String> states);
}
