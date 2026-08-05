package com.careerbridge.auth.repository;

import com.careerbridge.auth.model.PasswordResetOtp;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PasswordResetOtpRepository extends JpaRepository<PasswordResetOtp, Long> {

    // Newest row is the only one that can ever be valid -- see PasswordResetOtp's own javadoc.
    Optional<PasswordResetOtp> findTopByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<PasswordResetOtp> findByResetTokenAndUsedFalse(String resetToken);
}
