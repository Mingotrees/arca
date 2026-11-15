package com.popman.arca.repository;

import com.popman.arca.entity.RefreshToken;
import com.popman.arca.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByToken(String token);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT t FROM RefreshToken t WHERE t.token=:token")
    Optional<RefreshToken> findByTokenForUpdate(@org.springframework.data.repository.query.Param("token") String token);
    void deleteByUser(User user);
}
