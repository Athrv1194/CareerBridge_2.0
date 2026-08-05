package com.careerbridge.notification.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Consumer-side copy of the payload auth-service publishes on every forgot-password request
 * (exchange careerbridge.exchange, routing key password.reset.requested), including a resend.
 *
 * Carries the plaintext OTP -- this event exists solely to get that code in front of the user by
 * email; auth-service itself never sends mail.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PasswordResetRequestedEvent {

    private String email;
    private String firstName;
    private String otp;
    private int expiresInMinutes;
}
