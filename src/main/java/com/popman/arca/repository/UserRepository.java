package com.popman.arca.repository;

import com.popman.arca.entity.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserRepository extends JpaRepository<User, Long> {
    User findByEmail(String email);

    @Query("SELECT u FROM User u WHERE " +
            "LOWER(CONCAT(COALESCE(u.firstName, ''), ' ', COALESCE(u.lastName, ''))) LIKE LOWER(CONCAT('%', :query, '%')) ESCAPE '!' " +
            "OR LOWER(COALESCE(u.course, '')) LIKE LOWER(CONCAT('%', :query, '%')) ESCAPE '!' " +
            "OR LOWER(COALESCE(u.department, '')) LIKE LOWER(CONCAT('%', :query, '%')) ESCAPE '!' " +
            "OR LOWER(COALESCE(u.bio, '')) LIKE LOWER(CONCAT('%', :query, '%')) ESCAPE '!' " +
            "ORDER BY LOWER(COALESCE(u.lastName, '')), LOWER(COALESCE(u.firstName, '')), u.id")
    List<User> search(@Param("query") String query, Pageable pageable);
}
