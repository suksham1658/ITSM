package com.nbfc.itsm.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Set;

public interface HolidayRepository extends JpaRepository<Holiday, Long> {
    Set<Holiday> findByHolidayDateBetween(LocalDate from, LocalDate to);
    boolean existsByHolidayDate(LocalDate date);
}
