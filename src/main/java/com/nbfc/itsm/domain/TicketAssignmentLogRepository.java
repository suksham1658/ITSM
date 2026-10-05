package com.nbfc.itsm.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TicketAssignmentLogRepository extends JpaRepository<TicketAssignmentLog, Long> {
    List<TicketAssignmentLog> findByTicketOrderByCreatedAtUtcAscTicketAssignmentLogIdAsc(Ticket ticket);

    /** Hand-overs where this person was the target or the one who assigned. */
    List<TicketAssignmentLog> findByToEmployeeOrByEmployee(Employee to, Employee by);
}
