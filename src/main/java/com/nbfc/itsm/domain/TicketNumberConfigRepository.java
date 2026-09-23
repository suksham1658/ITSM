package com.nbfc.itsm.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import javax.persistence.LockModeType;
import java.util.Optional;

public interface TicketNumberConfigRepository extends JpaRepository<TicketNumberConfig, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from TicketNumberConfig c where c.sequenceYear = :year")
    Optional<TicketNumberConfig> findBySequenceYearForUpdate(@Param("year") int year);

    Optional<TicketNumberConfig> findFirstByOrderBySequenceYearDesc();
}
