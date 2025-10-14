package com.popman.arca.repository;

import com.popman.arca.entity.School;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SchoolRepository extends JpaRepository<School, Long> {
    boolean existsByName(String name);
}
