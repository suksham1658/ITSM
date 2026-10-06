package com.nbfc.itsm.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ImacDetailRepository extends JpaRepository<ImacDetail, Long> {

    Optional<ImacDetail> findByTicketId(Long ticketId);

    /** Hostnames already issued for a prefix (e.g. {@code AUTH-DEL-%}), to find the last number for a location. */
    @Query("select i.hostname from ImacDetail i where i.hostname like :prefix")
    List<String> findHostnamesLike(@Param("prefix") String prefix);
}
