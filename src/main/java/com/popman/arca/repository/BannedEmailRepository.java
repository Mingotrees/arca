package com.popman.arca.repository;

import com.popman.arca.entity.BannedEmail;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BannedEmailRepository extends JpaRepository<BannedEmail, Long> {
    boolean existsByEmailIgnoreCase(String email);
    Optional<BannedEmail> findByEmailIgnoreCase(String email);
    void deleteByEmailIgnoreCase(String email);
}
