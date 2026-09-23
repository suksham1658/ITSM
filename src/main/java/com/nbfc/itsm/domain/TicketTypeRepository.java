package com.nbfc.itsm.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TicketTypeRepository extends JpaRepository<TicketType, Long> {
    List<TicketType> findByActiveTrueOrderBySortOrderAsc();
    Optional<TicketType> findByCode(String code);
}
