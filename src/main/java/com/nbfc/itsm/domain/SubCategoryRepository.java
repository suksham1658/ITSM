package com.nbfc.itsm.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SubCategoryRepository extends JpaRepository<SubCategory, Long> {
    List<SubCategory> findByCategoryAndActiveTrueOrderBySortOrderAsc(Category category);
    List<SubCategory> findByActiveTrueOrderBySortOrderAsc();
}
