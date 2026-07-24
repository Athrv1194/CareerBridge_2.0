package com.careerbridge.auth.repository;

import com.careerbridge.auth.model.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByToken(String token);

    // Derived deletes run a select-then-delete and need an active transaction;
    // annotated here so this still works when called outside a @Transactional service method.
    @Transactional
    void deleteByUserId(Long userId);
}
