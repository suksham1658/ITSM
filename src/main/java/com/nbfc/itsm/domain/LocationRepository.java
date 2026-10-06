package com.nbfc.itsm.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LocationRepository extends JpaRepository<Location, Long> {

    List<Location> findByActiveTrueOrderBySortOrderAscNameAsc();

    List<Location> findAllByOrderBySortOrderAscNameAsc();

    Optional<Location> findByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndLocationIdNot(String name, Long locationId);

    boolean existsByNameIgnoreCase(String name);
}
