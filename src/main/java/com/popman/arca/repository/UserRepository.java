package com.popman.arca.repository;

import com.popman.arca.entity.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import java.util.Optional;

import java.util.List;

public interface UserRepository extends JpaRepository<User, Long> {
    User findByEmail(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") Long id);

    @Query("SELECT u FROM User u WHERE " +
            "LOWER(CONCAT(COALESCE(u.firstName, ''), ' ', COALESCE(u.lastName, ''))) LIKE LOWER(CONCAT('%', :query, '%')) ESCAPE '!' " +
            "OR LOWER(COALESCE(u.course, '')) LIKE LOWER(CONCAT('%', :query, '%')) ESCAPE '!' " +
            "OR LOWER(COALESCE(u.department, '')) LIKE LOWER(CONCAT('%', :query, '%')) ESCAPE '!' " +
            "OR LOWER(COALESCE(u.bio, '')) LIKE LOWER(CONCAT('%', :query, '%')) ESCAPE '!' " +
            "ORDER BY LOWER(COALESCE(u.lastName, '')), LOWER(COALESCE(u.firstName, '')), u.id")
    List<User> search(@Param("query") String query, Pageable pageable);
}
