package com.careerbridge.auth.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * One row per requested code, never overwritten -- a resend inserts a new row rather than
 * mutating the old one. {@code findTopByUserIdOrderByCreatedAtDesc} always resolves to this
 * newest row, which is what makes a resend silently invalidate whatever code the user already
 * has in their inbox: verifying against an older code compares against a hash that is no longer
 * the "latest", so it fails as an ordinary wrong-code rather than needing an explicit revoke.
 *
 * {@code resetToken} is null until verifyOtp succeeds, then holds a random opaque credential for
 * the final reset step -- same "plain token stored server-side" shape as RefreshToken, not a JWT,
 * so there is nothing to decode and no signing key shared with the access/refresh token flow.
 */
@Entity
@Table(name = "password_reset_otps")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PasswordResetOtp {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String otpHash;

    @Column(unique = true)
    private String resetToken;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @Builder.Default
    @Column(nullable = false)
    private Boolean used = false;

    /** Capped at MAX_ATTEMPTS in the service layer -- past that the code is treated as dead. */
    @Builder.Default
    @Column(nullable = false)
    private Integer attempts = 0;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
