package com.careerbridge.auth.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Published on every forgot-password request (including a resend). Consumed by
 * notification-service, which is the only thing that turns {@code otp} into an email --
 * auth-service never sends mail directly. Carries the plaintext code deliberately: this event
 * exists purely to get the code in front of the user, unlike the hash stored in
 * PasswordResetOtp.otpHash which is what verifyOtp actually checks against.
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
