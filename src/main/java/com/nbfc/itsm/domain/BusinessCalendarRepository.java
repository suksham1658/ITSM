package com.nbfc.itsm.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BusinessCalendarRepository extends JpaRepository<BusinessCalendar, Long> {
    List<BusinessCalendar> findAllByOrderByWeekdayIsoAsc();
}
